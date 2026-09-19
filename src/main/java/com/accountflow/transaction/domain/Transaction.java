package com.accountflow.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * One movement of money against one account. This collection is the financial
 * record: append-only and immutable once posted. Corrections are made by
 * posting a REVERSAL that references the original, never by editing history.
 *
 * <p>{@code balanceBefore} and {@code balanceAfter} are taken from the atomic
 * balance update's own return value, not from a separate read, so the pair is
 * the true before/after of this posting even under concurrency.
 *
 * <p>Compound index order follows ESR (equality, sort, range): the userId
 * equality first, then the date used for both sorting and range filtering, so
 * history queries need no in-memory sort.
 */
@Document(collection = "transactions")
@CompoundIndexes({
		@CompoundIndex(name = "user_date_idx", def = "{'userId': 1, 'transactionDate': -1}"),
		@CompoundIndex(name = "user_account_date_idx",
				def = "{'userId': 1, 'accountId': 1, 'transactionDate': -1}"),
		@CompoundIndex(name = "user_category_date_idx",
				def = "{'userId': 1, 'category': 1, 'transactionDate': -1}"),
		@CompoundIndex(name = "user_type_date_idx",
				def = "{'userId': 1, 'transactionType': 1, 'transactionDate': -1}"),
		@CompoundIndex(name = "user_merchant_idx", def = "{'userId': 1, 'merchant': 1}",
				partialFilter = "{'merchant': {'$type': 'string'}}") })
@Getter
@Setter
@Builder
public class Transaction {

	@Id
	private String id;

	@Indexed(unique = true)
	private String transactionReference;

	private String userId;

	private String accountId;

	/** Snapshot: the account may be renamed later, but history must not change. */
	private String accountName;

	private TransactionType transactionType;

	private Direction direction;

	/** Always positive; {@link #direction} carries the sign. */
	@Field(targetType = FieldType.DECIMAL128)
	private BigDecimal amount;

	private String currency;

	@Field(targetType = FieldType.DECIMAL128)
	private BigDecimal balanceBefore;

	@Field(targetType = FieldType.DECIMAL128)
	private BigDecimal balanceAfter;

	private String category;

	private String description;

	private String merchant;

	/** Business date, which the caller may set. Distinct from {@link #postedAt}. */
	private Instant transactionDate;

	/** System time the posting actually landed. */
	private Instant postedAt;

	@Builder.Default
	private TransactionStatus status = TransactionStatus.POSTED;

	@Indexed(sparse = true)
	private String transferId;

	private String transferReference;

	/** Set on a REVERSAL, pointing at the transaction it undoes. */
	@Indexed(sparse = true)
	private String reversalOf;

	/** Second line of defence behind the idempotency_keys collection. */
	@Indexed(unique = true, sparse = true)
	private String idempotencyKey;

	private Map<String, Object> metadata;

	@CreatedDate
	private Instant createdAt;

	@CreatedBy
	private String createdBy;

	public Transaction() {
	}

	public Transaction(String id, String transactionReference, String userId, String accountId, String accountName,
			TransactionType transactionType, Direction direction, BigDecimal amount, String currency,
			BigDecimal balanceBefore, BigDecimal balanceAfter, String category, String description, String merchant,
			Instant transactionDate, Instant postedAt, TransactionStatus status, String transferId,
			String transferReference, String reversalOf, String idempotencyKey, Map<String, Object> metadata,
			Instant createdAt, String createdBy) {
		this.id = id;
		this.transactionReference = transactionReference;
		this.userId = userId;
		this.accountId = accountId;
		this.accountName = accountName;
		this.transactionType = transactionType;
		this.direction = direction;
		this.amount = amount;
		this.currency = currency;
		this.balanceBefore = balanceBefore;
		this.balanceAfter = balanceAfter;
		this.category = category;
		this.description = description;
		this.merchant = merchant;
		this.transactionDate = transactionDate;
		this.postedAt = postedAt;
		this.status = status;
		this.transferId = transferId;
		this.transferReference = transferReference;
		this.reversalOf = reversalOf;
		this.idempotencyKey = idempotencyKey;
		this.metadata = metadata;
		this.createdAt = createdAt;
		this.createdBy = createdBy;
	}

}
