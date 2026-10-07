import { createClient } from "jsr:@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(supabaseUrl, serviceRoleKey);
const adminCookie = "privsex_links_admin";
const allowedOrigins = (Deno.env.get("ALLOWED_ORIGINS") || "*")
  .split(",").map((value) => value.trim()).filter(Boolean);

function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get("Origin") || "";
  const allowOrigin = allowedOrigins.includes("*") || allowedOrigins.includes(origin)
    ? (origin || "*") : (allowedOrigins[0] || "*");
  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Credentials": "true",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Vary": "Origin",
  };
}

function jsonResponse(req: Request, body: unknown, status = 200, extra: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders(req), "Content-Type": "application/json", ...extra },
  });
}

async function readSecret(key: string): Promise<string | null> {
  const { data, error } = await admin.from("app_secrets").select("value").eq("key", key).maybeSingle();
  if (error) throw error;
  return typeof data?.value === "string" ? data.value : data?.value == null ? null : JSON.stringify(data.value);
}

async function writeSecret(key: string, value: string) {
  const { error } = await admin.from("app_secrets").upsert({ key, value }, { onConflict: "key" });
  if (error) throw error;
}

async function avatarAsset() {
  const stored = await readSecret("PRIVSEX_LINKS_AVATAR_IMAGE");
  if (!stored) throw new Error("Avatar não encontrado no Supabase");
  const parsed = JSON.parse(stored);
  const contentType = String(parsed.contentType || "image/jpeg");
  const binary = Uint8Array.from(atob(String(parsed.data || "")), (char) => char.charCodeAt(0));
  return { contentType, binary };
}

function positionFrom(value: string | null) {
  const fallback = { x: 50, y: 50, zoom: 1, persisted: false };
  if (!value) return fallback;
  try {
    const parsed = JSON.parse(value);
    const clamp = (number: unknown, min: number, max: number, defaultValue: number) => {
      const value = Number(number);
      return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : defaultValue;
    };
    return {
      x: clamp(parsed.x, 0, 100, 50),
      y: clamp(parsed.y, 0, 100, 50),
      zoom: clamp(parsed.zoom, 1, 2, 1),
      persisted: true,
    };
  } catch {
    return fallback;
  }
}

function cookieValue(req: Request) {
  const cookies = req.headers.get("Cookie") || "";
  return cookies.split(";").map((part) => part.trim()).find((part) => part.startsWith(`${adminCookie}=`))?.slice(adminCookie.length + 1) || "";
}

async function digest(value: string) {
  const bytes = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(hash)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function isAdmin(req: Request) {
  const secret = Deno.env.get("ADMIN_SESSION_SECRET") || "";
  const token = cookieValue(req);
  if (!secret || !token) return false;
  const [expires, signature] = token.split(".");
  if (!expires || !signature || Number(expires) < Date.now()) return false;
  return (await digest(`${expires}.${secret}`)) === signature;
}

async function routePath(req: Request) {
  const pathname = new URL(req.url).pathname.replace(/\/+$/, "") || "/";
  const apiIndex = pathname.lastIndexOf("/api/");
  if (apiIndex >= 0) return pathname.slice(apiIndex);
  return pathname;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: corsHeaders(req) });
  const path = await routePath(req);

  try {
    if (req.method === "GET" && path === "/api/health") {
      return jsonResponse(req, { ok: true, service: "privsex-links-api" });
    }
    if (req.method === "GET" && path === "/api/avatar-image") {
      const avatar = await avatarAsset();
      return new Response(avatar.binary, {
        headers: { ...corsHeaders(req), "Content-Type": avatar.contentType, "Cache-Control": "public, max-age=3600" },
      });
    }
    if (req.method === "GET" && path === "/api/avatar-position") {
      return jsonResponse(req, positionFrom(await readSecret("PRIVSEX_AVATAR_POSITION")));
    }
    if (req.method === "GET" && path === "/api/admin/session") {
      return isAdmin(req) ? jsonResponse(req, { ok: true }) : jsonResponse(req, { message: "Sessão administrativa inválida" }, 401);
    }
    if (req.method === "POST" && path === "/api/admin/login") {
      const expected = Deno.env.get("ADMIN_PASSWORD") || await readSecret("PRIVSEX_LINKS_ADMIN_PASSWORD");
      const payload = await req.json().catch(() => ({}));
      if (!expected || String(payload.password || "") !== expected) return jsonResponse(req, { message: "Senha inválida" }, expected ? 401 : 503);
      const expires = String(Date.now() + 8 * 60 * 60 * 1000);
      const secret = Deno.env.get("ADMIN_SESSION_SECRET") || "";
      if (!secret) return jsonResponse(req, { message: "ADMIN_SESSION_SECRET não configurado" }, 503);
      const token = `${expires}.${await digest(`${expires}.${secret}`)}`;
      return jsonResponse(req, { ok: true }, 200, { "Set-Cookie": `${adminCookie}=${token}; Max-Age=28800; Path=/; HttpOnly; SameSite=None; Secure` });
    }
    if (req.method === "POST" && path === "/api/admin/logout") {
      return new Response(null, { status: 204, headers: { ...corsHeaders(req), "Set-Cookie": `${adminCookie}=; Max-Age=0; Path=/; HttpOnly; SameSite=None; Secure` } });
    }
    if (req.method === "POST" && path === "/api/admin/avatar-position") {
      if (!await isAdmin(req)) return jsonResponse(req, { message: "Sessão administrativa inválida" }, 401);
      const payload = await req.json().catch(() => ({}));
      const clamp = (number: unknown, min: number, max: number, fallback: number) => {
        const value = Number(number);
        return Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : fallback;
      };
      const value = JSON.stringify({ version: 2, x: clamp(payload.x, 0, 100, 50), y: clamp(payload.y, 0, 100, 50), zoom: clamp(payload.zoom, 1, 2, 1) });
      await writeSecret("PRIVSEX_AVATAR_POSITION", value);
      return jsonResponse(req, { ok: true, ...positionFrom(value) });
    }
    return jsonResponse(req, { message: "Not found", path }, 404);
  } catch (error) {
    console.error("privsex-links-api error", error);
    return jsonResponse(req, { message: "Erro interno da API" }, 500);
  }
});
