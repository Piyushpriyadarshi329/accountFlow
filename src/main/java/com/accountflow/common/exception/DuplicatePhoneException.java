package com.accountflow.common.exception;

public class DuplicatePhoneException extends BusinessException {

	public DuplicatePhoneException() {
		super(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number already registered");
	}

}
