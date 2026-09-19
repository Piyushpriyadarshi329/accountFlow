package com.accountflow.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A financial account. Monetary values are strings at the currency's exact scale.")
public record AccountResponse(@Schema(example = "6aae292ca0da9a703f6e3914") String id,

		@Schema(example = "HDFC Savings") String accountName,

		@Schema(example = "HDFC Bank") String bankName,

		@Schema(example = "50100123456789") String accountNumber,

		@Schema(example = "SAVINGS") AccountType accountType,

		@Schema(example = "INR") String currency,

		@Schema(type = "string", example = "50000.00") BigDecimal openingBalance,

		@Schema(type = "string", example = "40000.00") BigDecimal currentBalance,

		@Schema(type = "string", example = "0.00") BigDecimal creditLimit,

		@Schema(description = "What can actually be spent: current balance plus any credit line.",
				type = "string", example = "40000.00") BigDecimal availableBalance,

		@Schema(example = "ACTIVE") AccountStatus status,

		@Schema(description = "True for the single auto-created cash account.", example = "false")
		boolean cashWallet,

		@Schema(example = "2026-09-19T10:30:00Z") Instant createdAt,

		@Schema(example = "2026-09-19T10:30:00Z") Instant updatedAt) {
}
