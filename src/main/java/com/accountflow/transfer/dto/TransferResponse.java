package com.accountflow.transfer.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.accountflow.transfer.domain.TransferKind;
import com.accountflow.transfer.domain.TransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A completed transfer and the two ledger entries it produced. Only successful "
		+ "transfers are recorded: a failed one rolls back entirely and is reported as an error.")
public record TransferResponse(@Schema(example = "6aae2b41a0da9a703f6e3920") String id,

		@Schema(example = "TRF-MU7ZXNRJ-M49PS4Z8") String transferReference,

		@Schema(example = "6aae292ca0da9a703f6e3914") String sourceAccountId,

		@Schema(example = "HDFC Savings") String sourceAccountName,

		@Schema(example = "6aae292ca0da9a703f6e3915") String destinationAccountId,

		@Schema(example = "SBI Savings") String destinationAccountName,

		@Schema(type = "string", example = "10000.00") BigDecimal amount,

		@Schema(example = "INR") String currency,

		@Schema(example = "BANK_TO_BANK") TransferKind kind,

		@Schema(example = "COMPLETED") TransferStatus status,

		@Schema(description = "The TRANSFER_OUT leg on the source account.",
				example = "6aae2b41a0da9a703f6e3921") String debitTransactionId,

		@Schema(description = "The TRANSFER_IN leg on the destination account.",
				example = "6aae2b41a0da9a703f6e3922") String creditTransactionId,

		@Schema(example = "Monthly transfer") String description,

		@Schema(example = "2026-09-19T10:30:00Z") Instant createdAt,

		@Schema(example = "2026-09-19T10:30:00Z") Instant completedAt) {
}
