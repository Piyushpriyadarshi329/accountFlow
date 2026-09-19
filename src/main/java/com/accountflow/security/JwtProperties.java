package com.accountflow.security;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret HMAC key; must be at least 32 bytes for HS256. Supplied by the
 * environment, never defaulted — a hard-coded fallback is a production
 * vulnerability waiting for someone to forget the override.
 */
@Validated
@ConfigurationProperties(prefix = "accountflow.jwt")
public record JwtProperties(@NotBlank String secret, Duration accessTokenTtl, Duration refreshTokenTtl,
		String issuer) {

	public JwtProperties {
		accessTokenTtl = (accessTokenTtl != null) ? accessTokenTtl : Duration.ofMinutes(15);
		refreshTokenTtl = (refreshTokenTtl != null) ? refreshTokenTtl : Duration.ofDays(7);
		issuer = (issuer != null) ? issuer : "accountflow";
		if (secret != null && secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"accountflow.jwt.secret must be at least 32 bytes for HS256; generate one with: openssl rand -base64 48");
		}
	}

}
