package com.accountflow.admin.dto;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Creates a user directly, without the self-service registration flow.")
public record CreateUserRequest(

		@Schema(example = "Asha", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "First name is required") @Size(max = 60) String firstName,

		@Schema(example = "Menon") @Size(max = 60) String lastName,

		@Schema(example = "asha@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254) String email,

		@Schema(example = "+919812345678") @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$",
				message = "Phone must be in E.164 format, e.g. +919876543210") String phone,

		@Schema(description = "Stored as a BCrypt hash, exactly as self-registration does.",
				example = "Secret123", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Password is required") @Size(min = 8, max = 128,
				message = "Password must be between 8 and 128 characters") @Pattern(
						regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
						message = "Password must contain at least one letter and one digit") String password,

		@Schema(description = "Defaults to USER.", example = "USER") Role role,

		@Schema(description = "Defaults to ACTIVE.", example = "ACTIVE") UserStatus status) {
}
