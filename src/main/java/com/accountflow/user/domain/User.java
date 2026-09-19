package com.accountflow.user.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A person using AccountFlow. Distinct from an Account, which is a financial
 * account this user owns.
 *
 * <p>No Lombok {@code @Data} here on purpose: generated equals/hashCode over
 * mutable persisted state is a source of subtle bugs, and a generated toString
 * would eventually print the password hash.
 */
@Document(collection = "users")
@Getter
@Setter
@Builder
public class User {

	@Id
	private String id;

	private String firstName;

	private String lastName;

	/** Stored lowercase and trimmed so uniqueness is case-insensitive. */
	@Indexed(unique = true)
	private String email;

	@Indexed(unique = true, sparse = true)
	private String phone;

	/** BCrypt hash. Never leaves the repository layer. */
	@Field("passwordHash")
	private String passwordHash;

	@Builder.Default
	private Role role = Role.USER;

	@Builder.Default
	private UserStatus status = UserStatus.ACTIVE;

	@Builder.Default
	private int failedLoginAttempts = 0;

	private Instant lockedUntil;

	/** Bumped to invalidate every outstanding access token for this user. */
	@Builder.Default
	private int tokenVersion = 0;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	@CreatedBy
	private String createdBy;

	@LastModifiedBy
	private String updatedBy;

	public User() {
	}

	public User(String id, String firstName, String lastName, String email, String phone, String passwordHash,
			Role role, UserStatus status, int failedLoginAttempts, Instant lockedUntil, int tokenVersion,
			Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.phone = phone;
		this.passwordHash = passwordHash;
		this.role = role;
		this.status = status;
		this.failedLoginAttempts = failedLoginAttempts;
		this.lockedUntil = lockedUntil;
		this.tokenVersion = tokenVersion;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.createdBy = createdBy;
		this.updatedBy = updatedBy;
	}

	public String fullName() {
		return (this.lastName == null || this.lastName.isBlank()) ? this.firstName
				: this.firstName + " " + this.lastName;
	}

	/** A temporary lockout that has not yet expired. */
	public boolean isCurrentlyLocked() {
		return this.lockedUntil != null && this.lockedUntil.isAfter(Instant.now());
	}

}
