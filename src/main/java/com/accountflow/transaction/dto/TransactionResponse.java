package com.accountflow.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.TransactionStatus;
import com.accountflow.transaction.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One movement of money against one account. Immutable once posted: "
		+ "corrections are made by posting a REVERSAL, never by editing history.")
public record TransactionResponse(@Schema(example = "6aae11d2ee956131aeefc38d") String id,

		@Schema(description = "Human-readable, time-sortable business reference.",
				example = "TXN-MU7WDIXJ-5825V2G4") String transactionReference,

		@Schema(example = "6aae292ca0da9a703f6e3914") String accountId,

		@Schema(description = "Account name as it was at posting time, so a later rename cannot "
				+ "rewrite history.", example = "HDFC Savings") String accountName,

		@Schema(example = "DEBIT") TransactionType transactionType,

		@Schema(description = "Which way the money moved. Amounts are always positive.", example = "OUT")
		Direction direction,

		@Schema(type = "string", example = "3000.00") BigDecimal amount,

		@Schema(example = "INR") String currency,

		@Schema(type = "string", example = "15000.00") BigDecimal balanceBefore,

		@Schema(description = "Taken from the atomic balance update itself, so the pair is the true "
				+ "before/after of this posting even under concurrency.",
				type = "string", example = "12000.00") BigDecimal balanceAfter,

		@Schema(example = "SHOPPING") String category,

		@Schema(example = "Laptop purchase") String description,

		@Schema(example = "Amazon") String merchant,

		@Schema(example = "2026-09-19T10:30:00Z") Instant transactionDate,

		@Schema(example = "2026-09-19T10:30:00Z") Instant postedAt,

		@Schema(example = "POSTED") TransactionStatus status,

		@Schema(description = "Set on both legs of a transfer, linking the pair.",
				example = "TRF-MU7ZXNRJ-M49PS4Z8") String transferReference,

		@Schema(description = "Set on a REVERSAL, pointing at the transaction it undoes.") String reversalOf,

		@Schema(example = """
				{"paymentMethod":"UPI","referenceNumber":"UPI123456"}""") Map<String, Object> metadata) {
}
