package com.accountflow.auth.dto;

import com.accountflow.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An access token for API calls plus a refresh token to rotate it.")
public record TokenResponse(

		@Schema(description = "Send as: Authorization: Bearer <accessToken>",
				example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiI2YWFlMGNmMSJ9.Ux7f") String accessToken,

		@Schema(example = "ov-yE5EyDnXT2-yiMwO_yF6EpAUfvh402K12kaPp0uM") String refreshToken,

		@Schema(example = "Bearer") String tokenType,

		@Schema(description = "Lifetime of the access token in seconds.", example = "900") long expiresInSeconds,

		UserResponse user) {

	public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
		return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
	}

}
