package com.accountflow.user.domain;

public enum UserStatus {

	ACTIVE, INACTIVE, LOCKED;

	public boolean canAuthenticate() {
		return this == ACTIVE;
	}

}
