package com.accountflow.common.idempotency;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A claim on one money-moving request.
 *
 * <p>The {@code _id} is derived from user + endpoint + key, so the insert itself
 * is the mutual exclusion: two concurrent retries cannot both proceed. The
 * claim is written inside the same MongoDB transaction as the money movement,
 * so a crash can never leave a recorded success for money that did not move.
 */
@Document(collection = "idempotency_keys")
@Getter
@Setter
@Builder
public class IdempotencyRecord {

	@Id
	private String id;

	private String userId;

	private String endpoint;

	private String idempotencyKey;

	/** Hash of the request body, to catch a key reused with different content. */
	private String requestFingerprint;

	@Builder.Default
	private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;

	/** Id of the resource the original request produced, replayed on retry. */
	private String resourceId;

	/** Mongo removes the claim once this passes, bounding the collection. */
	@Indexed(expireAfter = "24h")
	private Instant createdAt;

	private Instant completedAt;

	public IdempotencyRecord() {
	}

	public IdempotencyRecord(String id, String userId, String endpoint, String idempotencyKey,
			String requestFingerprint, IdempotencyStatus status, String resourceId, Instant createdAt,
			Instant completedAt) {
		this.id = id;
		this.userId = userId;
		this.endpoint = endpoint;
		this.idempotencyKey = idempotencyKey;
		this.requestFingerprint = requestFingerprint;
		this.status = status;
		this.resourceId = resourceId;
		this.createdAt = createdAt;
		this.completedAt = completedAt;
	}

}
