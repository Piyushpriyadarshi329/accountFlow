package com.accountflow.account.dto;

import java.math.BigDecimal;

import com.accountflow.account.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Creates a bank account, card or wallet. The CASH account is created "
		+ "automatically on first use and cannot be added here.")
public record CreateAccountRequest(

		@Schema(example = "HDFC Savings", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Account name is required") @Size(max = 80) String accountName,

		@Schema(example = "HDFC Bank") @Size(max = 80) String bankName,

		@Schema(example = "SAVINGS", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "Account type is required") AccountType accountType,

		@Schema(description = "ISO-4217 code. Transfers between different currencies are rejected.",
				example = "INR", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Currency is required") @Pattern(regexp = "^[A-Z]{3}$",
				message = "Currency must be a 3-letter ISO-4217 code, e.g. INR") String currency,

		@Schema(description = "Optional. Unique per user, not globally.", example = "50100123456789")
		@Size(max = 40) String accountNumber,

		@Schema(description = "Starting balance. Becomes the current balance; it is not a transaction.",
				type = "string", example = "50000.00")
		@DecimalMin(value = "0.00", message = "Opening balance cannot be negative") BigDecimal openingBalance,

		@Schema(description = "How far below zero the account may go. Valid only on CREDIT_CARD.",
				type = "string", example = "0.00")
		@DecimalMin(value = "0.00", message = "Credit limit cannot be negative") BigDecimal creditLimit) {
}
