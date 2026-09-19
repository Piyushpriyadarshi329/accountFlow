package com.accountflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.accountflow.common.exception.AuthException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

/** Issues and verifies HS256 access tokens. */
@Component
public class JwtTokenProvider {

	static final String CLAIM_EMAIL = "email";

	static final String CLAIM_ROLE = "role";

	static final String CLAIM_TOKEN_VERSION = "tver";

	private final JwtProperties properties;

	private final SecretKey key;

	public JwtTokenProvider(JwtProperties properties) {
		this.properties = properties;
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(user.getId())
			.claim(CLAIM_EMAIL, user.getEmail())
			.claim(CLAIM_ROLE, user.getRole().name())
			.claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
			.id(UUID.randomUUID().toString())
			.issuer(this.properties.issuer())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(this.properties.accessTokenTtl())))
			.signWith(this.key)
			.compact();
	}

	public Claims parse(String token) {
		try {
			return Jwts.parser()
				.verifyWith(this.key)
				.requireIssuer(this.properties.issuer())
				.build()
				.parseSignedClaims(token)
				.getPayload();
		}
		catch (ExpiredJwtException ex) {
			throw new AuthException(ErrorCode.TOKEN_EXPIRED, "Access token has expired");
		}
		catch (JwtException | IllegalArgumentException ex) {
			throw new AuthException(ErrorCode.TOKEN_INVALID, "Access token is invalid");
		}
	}

	public java.time.Duration accessTokenTtl() {
		return this.properties.accessTokenTtl();
	}

	public java.time.Duration refreshTokenTtl() {
		return this.properties.refreshTokenTtl();
	}

}
