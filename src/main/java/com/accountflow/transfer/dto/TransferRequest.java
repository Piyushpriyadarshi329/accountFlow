package com.accountflow.transfer.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Moves money between two of your own accounts. Both legs commit together "
		+ "inside one MongoDB transaction, or neither does. Source and destination must differ "
		+ "and share a currency; there is no implicit conversion.")
public record TransferRequest(

		@Schema(example = "6aae292ca0da9a703f6e3914", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Source account is required") String sourceAccountId,

		@Schema(example = "6aae292ca0da9a703f6e3915", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Destination account is required") String destinationAccountId,

		@Schema(type = "string", example = "10000.00", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "Amount is required") @Positive(message = "Amount must be greater than zero")
		BigDecimal amount,

		@Schema(example = "Monthly transfer") @Size(max = 200) String description) {
}
