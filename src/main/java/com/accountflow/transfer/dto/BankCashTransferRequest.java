package com.accountflow.transfer.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Moves money between a bank account and the user's cash, in either direction. */
@Schema(description = "Moves money between a bank account and your cash. The cash side is resolved "
		+ "automatically, so only the bank account is named.")
public record BankCashTransferRequest(

		@Schema(description = "The bank account. It is the source for bank-to-cash and the "
				+ "destination for cash-to-bank.", example = "6aae292ca0da9a703f6e3914",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Account is required") String accountId,

		@Schema(type = "string", example = "10000.00", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "Amount is required") @Positive(message = "Amount must be greater than zero")
		BigDecimal amount,

		@Schema(example = "ATM withdrawal") @Size(max = 200) String description) {
}
