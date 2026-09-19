package com.accountflow.common.exception;

public class InsufficientBalanceException extends BusinessException {

	public InsufficientBalanceException() {
		super(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient account balance");
	}

}
