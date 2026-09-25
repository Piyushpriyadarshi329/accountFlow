package com.accountflow.admin.dto;

import java.time.Instant;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user as an administrator sees them, with a summary of what they hold.")
public record AdminUserResponse(@Schema(example = "6aae0cf1e3bf0dab89a49c81") String id,

		@Schema(example = "Piyush") String firstName,

		@Schema(example = "Priyadarshi") String lastName,

		@Schema(example = "piyush@example.com") String email,

		@Schema(example = "+919876543210") String phone,

		@Schema(example = "USER") Role role,

		@Schema(example = "ACTIVE") UserStatus status,

		@Schema(description = "Accounts the user owns, excluding the cash wallet.", example = "2")
		long accountCount,

		@Schema(description = "Ledger entries across all of their accounts.", example = "37")
		long transactionCount,

		@Schema(example = "0") int failedLoginAttempts,

		@Schema(description = "Set while a temporary lockout is in force.") Instant lockedUntil,

		@Schema(example = "2026-09-19T10:30:00Z") Instant createdAt,

		@Schema(example = "2026-09-19T10:30:00Z") Instant updatedAt) {
}
