package com.accountflow.common.exception;

public class TransferNotFoundException extends BusinessException {

	public TransferNotFoundException() {
		super(ErrorCode.TRANSFER_NOT_FOUND, "Transfer not found");
	}

}
