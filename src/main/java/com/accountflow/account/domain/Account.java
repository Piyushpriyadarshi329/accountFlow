package com.accountflow.account.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * A financial account belonging to exactly one user: a bank account, a credit
 * card, a wallet, or the single virtual CASH account holding physical cash.
 *
 * <p>Monetary fields are pinned to Decimal128. Spring Data's default mapping for
 * BigDecimal is not guaranteed to be numeric, and a balance stored as a string
 * would silently break {@code $inc} and every {@code $gte} balance guard.
 *
 * <p>{@code currentBalance} is only ever mutated by the ledger, through a
 * conditional atomic update. Nothing else may write it.
 */
@Document(collection = "accounts")
@CompoundIndexes({ @CompoundIndex(name = "user_status_idx", def = "{'userId': 1, 'status': 1}"),
		@CompoundIndex(name = "user_type_idx", def = "{'userId': 1, 'accountType': 1}"),
		@CompoundIndex(name = "user_accountnumber_unique", def = "{'userId': 1, 'accountNumber': 1}", unique = true,
				partialFilter = "{'accountNumber': {'$type': 'string'}}") })
@Getter
@Setter
@Builder
public class Account {

	@Id
	private String id;

	/** Owner. Every query is scoped by this; it is never taken from the client. */
	private String userId;

	/** Optional; unique per user, not globally - two users may hold a joint account. */
	private String accountNumber;

	private String accountName;

	private String bankName;

	private AccountType accountType;

	/** ISO-4217. */
	private String currency;

	@Field(targetType = FieldType.DECIMAL128)
	private BigDecimal openingBalance;

	@Field(targetType = FieldType.DECIMAL128)
	private BigDecimal currentBalance;

	/** How far below zero this account may go. Zero for everything but a credit card. */
	@Field(targetType = FieldType.DECIMAL128)
	@Builder.Default
	private BigDecimal creditLimit = BigDecimal.ZERO;

	@Builder.Default
	private AccountStatus status = AccountStatus.ACTIVE;

	/** Marks the auto-created cash account so it can be excluded from bank listings. */
	@Builder.Default
	private boolean systemCashWallet = false;

	/**
	 * Guards non-balance edits (rename, status change). Balance changes are
	 * protected by the ledger's conditional update instead, which needs no retry.
	 */
	@Version
	private Long version;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	@CreatedBy
	private String createdBy;

	@LastModifiedBy
	private String updatedBy;

	public Account() {
	}

	public Account(String id, String userId, String accountNumber, String accountName, String bankName,
			AccountType accountType, String currency, BigDecimal openingBalance, BigDecimal currentBalance,
			BigDecimal creditLimit, AccountStatus status, boolean systemCashWallet, Long version, Instant createdAt,
			Instant updatedAt, String createdBy, String updatedBy) {
		this.id = id;
		this.userId = userId;
		this.accountNumber = accountNumber;
		this.accountName = accountName;
		this.bankName = bankName;
		this.accountType = accountType;
		this.currency = currency;
		this.openingBalance = openingBalance;
		this.currentBalance = currentBalance;
		this.creditLimit = creditLimit;
		this.status = status;
		this.systemCashWallet = systemCashWallet;
		this.version = version;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.createdBy = createdBy;
		this.updatedBy = updatedBy;
	}

	public BigDecimal effectiveCreditLimit() {
		return (this.creditLimit != null) ? this.creditLimit : BigDecimal.ZERO;
	}

}
