package com.accountflow.common.exception;

import java.math.BigDecimal;

public class AccountNotEmptyException extends BusinessException {

	public AccountNotEmptyException(BigDecimal balance) {
		super(ErrorCode.ACCOUNT_NOT_EMPTY,
				"Account cannot be closed while its balance is " + balance.toPlainString());
	}

}
