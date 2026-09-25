package com.accountflow.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Sets a new password for a user and signs them out everywhere.")
public record ResetPasswordRequest(

		@Schema(example = "NewSecret123", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Password is required") @Size(min = 8, max = 128,
				message = "Password must be between 8 and 128 characters") @Pattern(
						regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
						message = "Password must contain at least one letter and one digit") String password) {
}
