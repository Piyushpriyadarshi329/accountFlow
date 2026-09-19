package com.accountflow.common.exception;

public class SameAccountTransferException extends BusinessException {

	public SameAccountTransferException() {
		super(ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and destination must be different accounts");
	}

}
