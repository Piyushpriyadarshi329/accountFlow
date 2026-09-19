package com.accountflow.user.dto;

import java.time.Instant;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user profile. Carries no password field of any kind.")
public record UserResponse(@Schema(example = "6aae0cf1e3bf0dab89a49c81") String id,

		@Schema(example = "Piyush") String firstName,

		@Schema(example = "Priyadarshi") String lastName,

		@Schema(example = "piyush@example.com") String email,

		@Schema(example = "+919876543210") String phone,

		@Schema(example = "USER") Role role,

		@Schema(example = "ACTIVE") UserStatus status,

		@Schema(example = "2026-09-19T10:30:00Z") Instant createdAt,

		@Schema(example = "2026-09-19T10:30:00Z") Instant updatedAt) {
}
