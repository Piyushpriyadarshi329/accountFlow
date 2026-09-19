package com.accountflow.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

	private final IdempotencyRecordRepository repository;

	public IdempotencyService(IdempotencyRecordRepository repository) {
		this.repository = repository;
	}

	public static String keyOf(String userId, String endpoint, String idempotencyKey) {
		return sha256(userId + ":" + endpoint + ":" + idempotencyKey);
	}

	public static String fingerprint(Object... parts) {
		StringBuilder joined = new StringBuilder();
		for (Object part : parts) {
			joined.append(part).append('|');
		}
		return sha256(joined.toString());
	}

	/**
	 * Inserts the claim. Must run inside the same transaction as the work it
	 * guards, so that a concurrent duplicate conflicts on the unique id and its
	 * whole transaction aborts.
	 */
	public IdempotencyRecord claim(String userId, String endpoint, String idempotencyKey, String fingerprint) {
		// insert, not save: save() with an explicit @Id performs an upsert, which
		// would silently overwrite the existing claim and defeat the whole guard.
		return this.repository.insert(IdempotencyRecord.builder()
			.id(keyOf(userId, endpoint, idempotencyKey))
			.userId(userId)
			.endpoint(endpoint)
			.idempotencyKey(idempotencyKey)
			.requestFingerprint(fingerprint)
			.status(IdempotencyStatus.IN_PROGRESS)
			.createdAt(Instant.now())
			.build());
	}

	public void complete(String recordId, String resourceId) {
		this.repository.findById(recordId).ifPresent((record) -> {
			record.setStatus(IdempotencyStatus.COMPLETED);
			record.setResourceId(resourceId);
			record.setCompletedAt(Instant.now());
			this.repository.save(record);
		});
	}

	public Optional<IdempotencyRecord> find(String userId, String endpoint, String idempotencyKey) {
		return this.repository.findById(keyOf(userId, endpoint, idempotencyKey));
	}

	/**
	 * Decides what a duplicate claim means. A different body under the same key
	 * is rejected rather than served someone else's result.
	 */
	public IdempotencyRecord resolveExisting(String userId, String endpoint, String idempotencyKey,
			String fingerprint) {
		IdempotencyRecord existing = find(userId, endpoint, idempotencyKey)
			.orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_IN_PROGRESS,
					"A matching request is already being processed; retry shortly"));

		if (!existing.getRequestFingerprint().equals(fingerprint)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
					"This Idempotency-Key was already used with a different request body");
		}
		if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
			throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS,
					"A matching request is already being processed; retry shortly");
		}
		return existing;
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
