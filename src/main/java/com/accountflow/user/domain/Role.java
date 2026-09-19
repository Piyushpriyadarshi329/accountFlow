package com.accountflow.user.domain;

public enum Role {

	USER, ADMIN;

	/** Spring Security expects the ROLE_ prefix on authorities. */
	public String authority() {
		return "ROLE_" + name();
	}

}
