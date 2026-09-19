package com.accountflow.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.TransactionType;

/**
 * One instruction to move money against one account. Credit, debit, each leg of
 * a transfer, and every cash operation all reduce to this, so the balance
 * guard and the ledger write exist in exactly one place.
 */
public record PostingCommand(String userId, String accountId, TransactionType type, Direction direction,
		BigDecimal amount, String currency, String category, String description, String merchant,
		Instant transactionDate, Map<String, Object> metadata, String transferId, String transferReference,
		String reversalOf, String idempotencyKey) {

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String userId;

		private String accountId;

		private TransactionType type;

		private Direction direction;

		private BigDecimal amount;

		private String currency;

		private String category;

		private String description;

		private String merchant;

		private Instant transactionDate;

		private Map<String, Object> metadata;

		private String transferId;

		private String transferReference;

		private String reversalOf;

		private String idempotencyKey;

		public Builder userId(String userId) {
			this.userId = userId;
			return this;
		}

		public Builder accountId(String accountId) {
			this.accountId = accountId;
			return this;
		}

		public Builder type(TransactionType type) {
			this.type = type;
			this.direction = type.direction();
			return this;
		}

		public Builder direction(Direction direction) {
			this.direction = direction;
			return this;
		}

		public Builder amount(BigDecimal amount) {
			this.amount = amount;
			return this;
		}

		public Builder currency(String currency) {
			this.currency = currency;
			return this;
		}

		public Builder category(String category) {
			this.category = category;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder merchant(String merchant) {
			this.merchant = merchant;
			return this;
		}

		public Builder transactionDate(Instant transactionDate) {
			this.transactionDate = transactionDate;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return this;
		}

		public Builder transfer(String transferId, String transferReference) {
			this.transferId = transferId;
			this.transferReference = transferReference;
			return this;
		}

		public Builder reversalOf(String reversalOf) {
			this.reversalOf = reversalOf;
			return this;
		}

		public Builder idempotencyKey(String idempotencyKey) {
			this.idempotencyKey = idempotencyKey;
			return this;
		}

		public PostingCommand build() {
			return new PostingCommand(this.userId, this.accountId, this.type, this.direction, this.amount,
					this.currency, this.category, this.description, this.merchant, this.transactionDate,
					this.metadata, this.transferId, this.transferReference, this.reversalOf, this.idempotencyKey);
		}

	}

}
