package com.accountflow.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.accountflow.auth.domain.RefreshToken;
import com.accountflow.auth.repository.RefreshTokenRepository;
import com.accountflow.common.exception.AuthException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Issues, rotates and revokes refresh tokens. */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository repository;

	private final JwtTokenProvider tokenProvider;

	public RefreshTokenService(RefreshTokenRepository repository, JwtTokenProvider tokenProvider) {
		this.repository = repository;
		this.tokenProvider = tokenProvider;
	}

	/** @return the raw token; only its hash is persisted. */
	public String issue(String userId, String family, String ipAddress, String userAgent) {
		String raw = generateRawToken();
		this.repository.save(RefreshToken.builder()
			.tokenHash(hash(raw))
			.userId(userId)
			.family((family != null) ? family : UUID.randomUUID().toString())
			.expiresAt(Instant.now().plus(this.tokenProvider.refreshTokenTtl()))
			.createdAt(Instant.now())
			.ipAddress(ipAddress)
			.userAgent(userAgent)
			.build());
		return raw;
	}

	/**
	 * Validates and consumes a refresh token, returning the record it came from.
	 * Replaying an already-rotated token is treated as theft: the entire family
	 * is revoked, logging out the attacker and the legitimate user alike.
	 */
	public RefreshToken consume(String rawToken) {
		RefreshToken stored = this.repository.findByTokenHash(hash(rawToken))
			.orElseThrow(() -> new AuthException(ErrorCode.TOKEN_INVALID, "Refresh token is invalid"));

		if (stored.isRevoked()) {
			log.warn("Refresh token reuse detected for user {} family {} - revoking family", stored.getUserId(),
					stored.getFamily());
			revokeFamily(stored.getFamily());
			throw new AuthException(ErrorCode.REFRESH_TOKEN_REUSED,
					"Refresh token has already been used; please sign in again");
		}
		if (stored.isExpired()) {
			throw new AuthException(ErrorCode.TOKEN_EXPIRED, "Refresh token has expired");
		}
		return stored;
	}

	public String rotate(RefreshToken consumed, String ipAddress, String userAgent) {
		String raw = generateRawToken();
		consumed.setRevoked(true);
		consumed.setRevokedAt(Instant.now());
		consumed.setReplacedByHash(hash(raw));
		this.repository.save(consumed);

		this.repository.save(RefreshToken.builder()
			.tokenHash(hash(raw))
			.userId(consumed.getUserId())
			.family(consumed.getFamily())
			.expiresAt(Instant.now().plus(this.tokenProvider.refreshTokenTtl()))
			.createdAt(Instant.now())
			.ipAddress(ipAddress)
			.userAgent(userAgent)
			.build());
		return raw;
	}

	public void revokeFamily(String family) {
		List<RefreshToken> active = this.repository.findByFamilyAndRevokedFalse(family);
		active.forEach((token) -> {
			token.setRevoked(true);
			token.setRevokedAt(Instant.now());
		});
		this.repository.saveAll(active);
	}

	public void revokeAllForUser(String userId) {
		List<RefreshToken> active = this.repository.findByUserIdAndRevokedFalse(userId);
		active.forEach((token) -> {
			token.setRevoked(true);
			token.setRevokedAt(Instant.now());
		});
		this.repository.saveAll(active);
	}

	private static String generateRawToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
