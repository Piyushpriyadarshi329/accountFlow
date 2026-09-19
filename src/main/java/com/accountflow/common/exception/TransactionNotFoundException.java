package com.accountflow.common.exception;

public class TransactionNotFoundException extends BusinessException {

	public TransactionNotFoundException() {
		super(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found");
	}

}
