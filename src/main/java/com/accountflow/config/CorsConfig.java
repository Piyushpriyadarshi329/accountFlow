package com.accountflow.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin access for browser clients - the Expo web build of the mobile
 * app, and Swagger UI when served from elsewhere.
 *
 * <p>Native iOS and Android are not browsers and ignore CORS entirely, so this
 * exists purely for the web target.
 *
 * <p>Origins come from configuration and default to the local Expo dev server.
 * Deliberately not a wildcard: this API is authenticated with a bearer token,
 * and an over-permissive policy lets any site a signed-in user visits script
 * requests against it.
 */
@Configuration
public class CorsConfig {

	private final List<String> allowedOrigins;

	public CorsConfig(
			@Value("${accountflow.cors.allowed-origins:http://localhost:8081,http://localhost:19006}") List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(this.allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(
				List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Request-Id"));
		// So a browser client can read the correlation id off the response.
		configuration.setExposedHeaders(List.of("X-Request-Id"));
		// Tokens travel in the Authorization header, not cookies, so credentials
		// are not needed - and leaving them off keeps the policy tighter.
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

}
