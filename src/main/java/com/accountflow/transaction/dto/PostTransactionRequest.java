package com.accountflow.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for a credit or debit. The two share a shape; the endpoint decides the
 * direction, so a client cannot flip a debit into a credit by changing a field.
 */
@Schema(description = "A credit or debit. The endpoint decides the direction, so the amount is "
		+ "always positive. Requires an Idempotency-Key header.")
public record PostTransactionRequest(

		@Schema(description = "Positive, and no more decimal places than the currency allows. "
				+ "An over-precise amount is rejected rather than rounded.",
				type = "string", example = "5000.00", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "Amount is required") @Positive(message = "Amount must be greater than zero")
		BigDecimal amount,

		@Schema(description = "FOOD, TRAVEL, SHOPPING, RENT, SALARY, INVESTMENT, EMI, BILLS, MEDICAL, "
				+ "ENTERTAINMENT, TRANSFER, CASH, OTHER, or your own.", example = "SHOPPING")
		@Size(max = 40) String category,

		@Schema(example = "Laptop purchase") @Size(max = 200) String description,

		@Schema(description = "Indexed, so transactions can be searched by merchant.", example = "Amazon")
		@Size(max = 80) String merchant,

		@Schema(description = "Business date. Defaults to now. Distinct from postedAt, the system time.",
				example = "2026-09-19T10:30:00Z") Instant transactionDate,

		@Schema(description = "Free-form extra detail, shaped however the transaction type needs.",
				example = """
						{"paymentMethod":"UPI","referenceNumber":"UPI123456","notes":"Laptop purchase"}""")
		Map<String, Object> metadata) {
}
