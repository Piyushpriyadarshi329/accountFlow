package com.accountflow.common.exception;

public class DuplicateEmailException extends BusinessException {

	public DuplicateEmailException() {
		super(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already registered");
	}

}
