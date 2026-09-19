package com.accountflow.auth.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A refresh token, stored only as a SHA-256 hash — a database leak must not
 * hand an attacker usable tokens.
 *
 * <p>Tokens issued from one login share a {@code family}. Presenting an already
 * rotated token means it leaked, so the whole family is revoked.
 */
@Document(collection = "refresh_tokens")
@Getter
@Setter
@Builder
public class RefreshToken {

	@Id
	private String id;

	@Indexed(unique = true)
	private String tokenHash;

	@Indexed
	private String userId;

	/** Shared by every token descended from a single login. */
	@Indexed
	private String family;

	/** Mongo removes the document once this passes. */
	@Indexed(expireAfter = "0s")
	private Instant expiresAt;

	@Builder.Default
	private boolean revoked = false;

	private Instant revokedAt;

	private String replacedByHash;

	private Instant createdAt;

	private String ipAddress;

	private String userAgent;

	public RefreshToken() {
	}

	public RefreshToken(String id, String tokenHash, String userId, String family, Instant expiresAt, boolean revoked,
			Instant revokedAt, String replacedByHash, Instant createdAt, String ipAddress, String userAgent) {
		this.id = id;
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.family = family;
		this.expiresAt = expiresAt;
		this.revoked = revoked;
		this.revokedAt = revokedAt;
		this.replacedByHash = replacedByHash;
		this.createdAt = createdAt;
		this.ipAddress = ipAddress;
		this.userAgent = userAgent;
	}

	public boolean isExpired() {
		return this.expiresAt.isBefore(Instant.now());
	}

}
