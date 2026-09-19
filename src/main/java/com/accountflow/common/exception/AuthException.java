package com.accountflow.common.exception;

/**
 * Authentication and token failures. Deliberately vague messages: never reveal
 * whether it was the email or the password that was wrong.
 */
public class AuthException extends BusinessException {

	public AuthException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public static AuthException invalidCredentials() {
		return new AuthException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
	}

}
