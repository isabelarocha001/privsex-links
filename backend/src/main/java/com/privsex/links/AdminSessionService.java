package com.privsex.links;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AdminSessionService {
  public static final String COOKIE = "privsex_admin_token";
  private final String password;
  private final String secret;
  private final boolean secure;

  public AdminSessionService(@Value("${privsex.admin-password}") String password,
      @Value("${privsex.admin-session-secret}") String secret,
      @Value("${privsex.cookie-secure}") boolean secure) {
    this.password = password;
    this.secret = secret;
    this.secure = secure;
  }

  public boolean passwordMatches(String candidate) {
    return !password.isBlank() && MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), (candidate == null ? "" : candidate).getBytes(StandardCharsets.UTF_8));
  }

  public String issue() {
    long expires = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30;
    String payload = "admin:" + expires;
    return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + "." + sign(payload)).getBytes(StandardCharsets.UTF_8));
  }

  public boolean valid(String token) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      int dot = raw.lastIndexOf('.');
      if (dot < 1 || !MessageDigest.isEqual(raw.substring(dot + 1).getBytes(StandardCharsets.UTF_8), sign(raw.substring(0, dot)).getBytes(StandardCharsets.UTF_8))) return false;
      return raw.startsWith("admin:") && Long.parseLong(raw.substring(6, dot)) > System.currentTimeMillis();
    } catch (Exception ex) { return false; }
  }

  public ResponseCookie cookie(String value) {
    return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(60L * 60 * 24 * 30).build();
  }

  public ResponseCookie clearCookie() {
    return ResponseCookie.from(COOKIE, "").httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(0).build();
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      StringBuilder hex = new StringBuilder();
      for (byte b : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (Exception ex) { throw new IllegalStateException("Sessão admin não configurada", ex); }
  }
}
