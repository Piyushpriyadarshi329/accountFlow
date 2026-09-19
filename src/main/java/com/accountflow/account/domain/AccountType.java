package com.accountflow.account.domain;

public enum AccountType {

	SAVINGS, CURRENT, SALARY, CREDIT_CARD, WALLET, CASH, OTHER;

	/**
	 * Only a credit card is a liability account, so only it may carry a credit
	 * limit and settle below zero.
	 */
	public boolean supportsCreditLimit() {
		return this == CREDIT_CARD;
	}

	/** The single virtual account holding the user's physical cash. */
	public boolean isCash() {
		return this == CASH;
	}

}
