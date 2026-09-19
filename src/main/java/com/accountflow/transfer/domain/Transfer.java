package com.accountflow.transfer.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * The record tying a transfer's two ledger legs together. Both legs and this
 * document commit in a single MongoDB transaction, so a transfer is either
 * wholly visible or entirely absent - a half-applied transfer cannot be read.
 */
@Document(collection = "transfers")
@CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
@Getter
@Setter
@Builder
public class Transfer {

	@Id
	private String id;

	@Indexed(unique = true)
	private String transferReference;

	private String userId;

	private String sourceAccountId;

	private String destinationAccountId;

	/** Snapshots, so renaming an account later cannot rewrite this record. */
	private String sourceAccountName;

	private String destinationAccountName;

	@Field(targetType = FieldType.DECIMAL128)
	private BigDecimal amount;

	private String currency;

	private TransferKind kind;

	@Builder.Default
	private TransferStatus status = TransferStatus.COMPLETED;

	private String debitTransactionId;

	private String creditTransactionId;

	private String description;

	@Indexed(unique = true, sparse = true)
	private String idempotencyKey;

	private String failureReason;

	private Instant createdAt;

	private Instant completedAt;

	@CreatedBy
	private String createdBy;

	public Transfer() {
	}

	public Transfer(String id, String transferReference, String userId, String sourceAccountId,
			String destinationAccountId, String sourceAccountName, String destinationAccountName, BigDecimal amount,
			String currency, TransferKind kind, TransferStatus status, String debitTransactionId,
			String creditTransactionId, String description, String idempotencyKey, String failureReason,
			Instant createdAt, Instant completedAt, String createdBy) {
		this.id = id;
		this.transferReference = transferReference;
		this.userId = userId;
		this.sourceAccountId = sourceAccountId;
		this.destinationAccountId = destinationAccountId;
		this.sourceAccountName = sourceAccountName;
		this.destinationAccountName = destinationAccountName;
		this.amount = amount;
		this.currency = currency;
		this.kind = kind;
		this.status = status;
		this.debitTransactionId = debitTransactionId;
		this.creditTransactionId = creditTransactionId;
		this.description = description;
		this.idempotencyKey = idempotencyKey;
		this.failureReason = failureReason;
		this.createdAt = createdAt;
		this.completedAt = completedAt;
		this.createdBy = createdBy;
	}

}
