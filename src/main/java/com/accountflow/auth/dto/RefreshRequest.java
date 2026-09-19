package com.accountflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Exchanges a refresh token for a new pair. The presented token is consumed: "
		+ "presenting it again is treated as theft and revokes the whole token family.")
public record RefreshRequest(

		@Schema(example = "ov-yE5EyDnXT2-yiMwO_yF6EpAUfvh402K12kaPp0uM",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "Refresh token is required") String refreshToken) {
}
