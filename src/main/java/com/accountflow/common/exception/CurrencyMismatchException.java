package com.accountflow.common.exception;

public class CurrencyMismatchException extends BusinessException {

	public CurrencyMismatchException(String expected, String actual) {
		super(ErrorCode.CURRENCY_MISMATCH, "Account currency is " + expected + " but the request specified " + actual);
	}

}
