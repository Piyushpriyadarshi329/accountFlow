package com.accountflow.common.exception;

/**
 * Base for every expected failure. Anything thrown from a service that the
 * caller could reasonably act on should extend this, so the global handler can
 * map it to a stable code instead of a 500.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return this.errorCode;
	}

}
