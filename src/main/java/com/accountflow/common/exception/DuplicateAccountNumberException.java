package com.accountflow.common.exception;

public class DuplicateAccountNumberException extends BusinessException {

	public DuplicateAccountNumberException() {
		super(ErrorCode.DUPLICATE_ACCOUNT_NUMBER, "You already have an account with this account number");
	}

}
