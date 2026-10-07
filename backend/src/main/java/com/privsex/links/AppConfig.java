package com.privsex.links;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class AppConfig {
  @Bean
  HttpClient httpClient() { return HttpClient.newHttpClient(); }

  @Bean
  CorsFilter corsFilter(@Value("${privsex.allowed-origins}") String allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    for (String origin : allowedOrigins.split(",")) config.addAllowedOrigin(origin.trim());
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }
}
