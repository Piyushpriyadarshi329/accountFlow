package com.accountflow.common.exception;

import com.accountflow.account.domain.AccountStatus;

public class AccountClosedException extends BusinessException {

	public AccountClosedException(AccountStatus status) {
		super((status == AccountStatus.CLOSED) ? ErrorCode.ACCOUNT_CLOSED : ErrorCode.ACCOUNT_INACTIVE,
				"Account is " + status.name().toLowerCase() + " and cannot accept transactions");
	}

}
