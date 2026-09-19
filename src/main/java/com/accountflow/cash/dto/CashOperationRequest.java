package com.accountflow.cash.dto;

import java.math.BigDecimal;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Records physical cash coming in or going out. To move cash to or from a bank "
		+ "account, use /api/v1/transfers/bank-to-cash or /cash-to-bank instead.")
public record CashOperationRequest(

		@Schema(type = "string", example = "2000.00", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "Amount is required") @Positive(message = "Amount must be greater than zero")
		BigDecimal amount,

		@Schema(example = "Cash received") @Size(max = 200) String description,

		@Schema(example = """
				{"source":"gift"}""") Map<String, Object> metadata) {
}
