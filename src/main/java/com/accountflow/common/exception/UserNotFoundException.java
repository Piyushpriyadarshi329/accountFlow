package com.accountflow.common.exception;

public class UserNotFoundException extends BusinessException {

	public UserNotFoundException(String message) {
		super(ErrorCode.USER_NOT_FOUND, message);
	}

	public UserNotFoundException() {
		this("User not found");
	}

}
