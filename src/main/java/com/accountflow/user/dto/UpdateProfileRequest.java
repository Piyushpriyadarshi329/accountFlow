package com.accountflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Updates the signed-in user's profile. Email and role are not changeable here: "
		+ "changing an email is an identity change and needs its own verified flow.")
public record UpdateProfileRequest(

		@Schema(example = "Piyush", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "First name is required") @Size(max = 60) String firstName,

		@Schema(example = "Priyadarshi") @Size(max = 60) String lastName,

		@Schema(example = "+919876543210") @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$",
				message = "Phone must be in E.164 format, e.g. +919876543210") String phone) {
}
