package com.accountflow.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Updates descriptive fields only. Currency, type and balance are immutable: "
		+ "changing them would rewrite the meaning of existing history.")
public record UpdateAccountRequest(

		@Schema(example = "HDFC Salary Account", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Account name is required") @Size(max = 80) String accountName,

		@Schema(example = "HDFC Bank") @Size(max = 80) String bankName,

		@Schema(example = "50100123456789") @Size(max = 40) String accountNumber) {
}
