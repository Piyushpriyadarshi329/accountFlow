package com.accountflow.common.exception;

/**
 * Also used when an account exists but belongs to someone else. Answering 404
 * rather than 403 keeps the API from confirming that an id exists, which would
 * make it an enumeration oracle.
 */
public class AccountNotFoundException extends BusinessException {

	public AccountNotFoundException() {
		super(ErrorCode.ACCOUNT_NOT_FOUND, "Account not found");
	}

}
