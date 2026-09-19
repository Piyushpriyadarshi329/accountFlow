package com.accountflow.account.domain;

public enum AccountStatus {

	ACTIVE, INACTIVE, CLOSED;

	public boolean acceptsPostings() {
		return this == ACTIVE;
	}

}
