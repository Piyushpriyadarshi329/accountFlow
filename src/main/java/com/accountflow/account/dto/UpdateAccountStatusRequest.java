package com.accountflow.account.dto;

import com.accountflow.account.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Changes an account's status. Closing requires a zero balance, and a closed "
		+ "or inactive account accepts no further transactions.")
public record UpdateAccountStatusRequest(

		@Schema(example = "CLOSED", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "Status is required") AccountStatus status) {
}
