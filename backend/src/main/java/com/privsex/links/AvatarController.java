package com.privsex.links;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AvatarController {
  private static final String POSITION_KEY = "PRIVSEX_LINKS_AVATAR_POSITION";
  private final SupabaseSecretsService secrets;
  private final AdminSessionService sessions;
  private final ObjectMapper json;

  public AvatarController(SupabaseSecretsService secrets, AdminSessionService sessions, ObjectMapper json) {
    this.secrets = secrets;
    this.sessions = sessions;
    this.json = json;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("ok", true, "service", "privsex-links-api");
  }

  @GetMapping("/avatar-image")
  public ResponseEntity<byte[]> avatarImage() {
    SupabaseSecretsService.AvatarAsset avatar = secrets.readAvatar();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(avatar.contentType()))
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
        .body(avatar.bytes());
  }

  @GetMapping("/avatar-position")
  public ResponseEntity<Map<String, Object>> avatarPosition() {
    String stored = secrets.read(POSITION_KEY);
    Map<String, Object> result = defaultPosition();
    if (stored != null && !stored.isBlank()) {
      try {
        JsonNode node = json.readTree(stored);
        result = Map.of("x", clamp(node.path("x").asDouble(50), 0, 100),
            "y", clamp(node.path("y").asDouble(50), 0, 100),
            "zoom", clamp(node.path("zoom").asDouble(1), 1, 2),
            "persisted", true);
      } catch (Exception ignored) {
        // Mantém o padrão se um valor antigo estiver inválido.
      }
    }
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(result);
  }

  @PostMapping("/admin/login")
  public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
    if (!sessions.passwordMatches(request == null ? null : request.password())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Senha inválida"));
    }
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, sessions.cookie(sessions.issue()).toString())
        .body(Map.of("ok", true));
  }

  @GetMapping("/admin/session")
  public ResponseEntity<Map<String, Object>> session(HttpServletRequest request) {
    return isAdmin(request) ? ResponseEntity.ok(Map.of("ok", true))
        : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Sessão administrativa inválida"));
  }

  @PostMapping("/admin/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clearCookie().toString()).build();
  }

  @PostMapping("/admin/avatar-position")
  public ResponseEntity<Map<String, Object>> saveAvatarPosition(HttpServletRequest request,
      @RequestBody Position position) {
    if (!isAdmin(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    double x = clamp(position == null ? 50 : position.x(), 0, 100);
    double y = clamp(position == null ? 50 : position.y(), 0, 100);
    double zoom = clamp(position == null ? 1 : position.zoom(), 1, 2);
    try {
      secrets.upsert(POSITION_KEY, json.createObjectNode().put("version", 2).put("x", x).put("y", y).put("zoom", zoom).toString());
      return ResponseEntity.ok(Map.of("ok", true, "x", x, "y", y, "zoom", zoom));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }
  }

  private boolean isAdmin(HttpServletRequest request) {
    if (request.getCookies() == null) return false;
    for (Cookie cookie : request.getCookies()) {
      if (AdminSessionService.COOKIE.equals(cookie.getName()) && sessions.valid(cookie.getValue())) return true;
    }
    return false;
  }

  private static Map<String, Object> defaultPosition() {
    return Map.of("x", 50d, "y", 50d, "zoom", 1d, "persisted", false);
  }

  private static double clamp(double value, double min, double max) {
    if (!Double.isFinite(value)) return min == 0 ? 50 : 1;
    return Math.min(max, Math.max(min, value));
  }

  public record LoginRequest(String password) {}
  public record Position(double x, double y, double zoom) {}
}
