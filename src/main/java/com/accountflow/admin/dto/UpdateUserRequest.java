package com.accountflow.admin.dto;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Updates a user. Email is not changeable here - it is the login identity and "
		+ "needs a verified flow of its own. Password is not changeable here either; use the reset endpoint.")
public record UpdateUserRequest(

		@Schema(example = "Asha", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "First name is required") @Size(max = 60) String firstName,

		@Schema(example = "Menon") @Size(max = 60) String lastName,

		@Schema(example = "+919812345678") @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$",
				message = "Phone must be in E.164 format, e.g. +919876543210") String phone,

		@Schema(example = "USER") Role role,

		@Schema(description = "Moving a user out of ACTIVE revokes their tokens immediately.",
				example = "ACTIVE") UserStatus status) {
}
