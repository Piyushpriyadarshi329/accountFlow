package com.accountflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials. An unknown email and a wrong password fail identically, "
		+ "so the response cannot be used to discover which accounts exist.")
public record LoginRequest(

		@Schema(example = "piyush@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Email is required") String email,

		@Schema(example = "Secret123", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Password is required") String password) {
}
