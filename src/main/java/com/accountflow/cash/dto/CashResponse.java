package com.accountflow.cash.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.accountflow.account.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Physical cash held by the signed-in user. Backed by an account of type CASH, "
		+ "so it shares the ledger, balance guard and audit trail with bank accounts.")
public record CashResponse(@Schema(example = "6aae292ca0da9a703f6e3916") String accountId,

		@Schema(example = "INR") String currency,

		@Schema(type = "string", example = "6500.00") BigDecimal balance,

		@Schema(example = "ACTIVE") AccountStatus status,

		@Schema(example = "2026-09-19T10:30:00Z") Instant updatedAt) {
}
