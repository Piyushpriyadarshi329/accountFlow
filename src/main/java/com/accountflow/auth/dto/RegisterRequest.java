package com.accountflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "New user registration. Returns an access and refresh token on success.")
public record RegisterRequest(

		@Schema(example = "Piyush", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "First name is required") @Size(max = 60) String firstName,

		@Schema(example = "Priyadarshi") @Size(max = 60) String lastName,

		@Schema(description = "Stored lowercased, so uniqueness is case-insensitive.",
				example = "piyush@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254) String email,

		@Schema(description = "Optional, E.164 format. Unique across users when given.",
				example = "+919876543210")
		@Pattern(regexp = "^\\+?[1-9]\\d{7,14}$",
				message = "Phone must be in E.164 format, e.g. +919876543210") String phone,

		@Schema(description = "At least 8 characters, with a letter and a digit. Stored as a BCrypt hash.",
				example = "Secret123", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Password is required") @Size(min = 8, max = 128,
				message = "Password must be between 8 and 128 characters") @Pattern(
						regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
						message = "Password must contain at least one letter and one digit") String password) {
}
