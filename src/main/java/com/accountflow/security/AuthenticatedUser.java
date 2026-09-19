package com.accountflow.security;

import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;

/**
 * The authenticated principal. Controllers receive this via
 * {@code @AuthenticationPrincipal} and must take the user id from here —
 * never from a path variable, body field or header.
 */
public record AuthenticatedUser(String userId, String email, Role role) {

	public static AuthenticatedUser from(User user) {
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
	}

	public boolean isAdmin() {
		return this.role == Role.ADMIN;
	}

}
