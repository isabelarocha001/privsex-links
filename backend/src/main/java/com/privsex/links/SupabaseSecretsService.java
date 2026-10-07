package com.privsex.links;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class SupabaseSecretsService {
  private final HttpClient http;
  private final ObjectMapper json;
  private final String baseUrl;
  private final String serviceKey;

  public SupabaseSecretsService(HttpClient http, ObjectMapper json,
      @Value("${privsex.supabase-url}") String baseUrl,
      @Value("${privsex.supabase-service-key}") String serviceKey) {
    this.http = http;
    this.json = json;
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.serviceKey = serviceKey;
  }

  public String read(String key) {
    if (serviceKey.isBlank()) throw new IllegalStateException("SUPABASE_SERVICE_KEY não configurada");
    try {
      String url = baseUrl + "/rest/v1/app_secrets?select=value&key=eq." + encode(key);
      HttpRequest request = base(url).GET().build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() / 100 != 2) throw new IllegalStateException("Supabase GET falhou: HTTP " + response.statusCode());
      JsonNode rows = json.readTree(response.body());
      if (!rows.isArray() || rows.isEmpty()) return null;
      JsonNode value = rows.get(0).path("value");
      return value.isTextual() ? value.asText() : value.toString();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Supabase interrompeu a consulta", ex);
    } catch (Exception ex) {
      throw new IllegalStateException("Não foi possível consultar o Supabase", ex);
    }
  }

  public void upsert(String key, String value) {
    if (serviceKey.isBlank()) throw new IllegalStateException("SUPABASE_SERVICE_KEY não configurada");
    try {
      String body = json.createObjectNode().put("key", key).put("value", value).toString();
      String url = baseUrl + "/rest/v1/app_secrets?on_conflict=key";
      HttpRequest request = base(url).header("Prefer", "resolution=merge-duplicates,return=minimal")
          .POST(HttpRequest.BodyPublishers.ofString("[" + body + "]", StandardCharsets.UTF_8)).build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() / 100 != 2) throw new IllegalStateException("Supabase upsert falhou: HTTP " + response.statusCode());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Supabase interrompeu a gravação", ex);
    } catch (Exception ex) {
      throw new IllegalStateException("Não foi possível gravar no Supabase", ex);
    }
  }

  public AvatarAsset readAvatar() {
    try {
      String stored = read("PRIVSEX_LINKS_AVATAR_IMAGE");
      if (stored == null) throw new IllegalStateException("Avatar não encontrado no Supabase");
      JsonNode node = json.readTree(stored);
      String contentType = node.path("contentType").asText("image/jpeg");
      byte[] bytes = Base64.getDecoder().decode(node.path("data").asText());
      return new AvatarAsset(contentType, bytes);
    } catch (Exception ex) {
      throw new IllegalStateException("Avatar inválido no Supabase", ex);
    }
  }

  private HttpRequest.Builder base(String url) {
    return HttpRequest.newBuilder(URI.create(url)).header("apikey", serviceKey)
        .header("Authorization", "Bearer " + serviceKey).header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
  }

  private static String encode(String value) {
    // A chave é usada como literal no filtro PostgREST. Base64 aqui faria o
    // filtro procurar outro valor (e quebraria a leitura de app_secrets).
    return value;
  }

  public record AvatarAsset(String contentType, byte[] bytes) {}
}
