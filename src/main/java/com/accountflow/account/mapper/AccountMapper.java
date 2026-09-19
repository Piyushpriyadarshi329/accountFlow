package com.accountflow.account.mapper;

import com.accountflow.account.domain.Account;
import com.accountflow.account.dto.AccountResponse;

public final class AccountMapper {

	private AccountMapper() {
	}

	public static AccountResponse toResponse(Account account) {
		return new AccountResponse(account.getId(), account.getAccountName(), account.getBankName(),
				account.getAccountNumber(), account.getAccountType(), account.getCurrency(),
				account.getOpeningBalance(), account.getCurrentBalance(), account.effectiveCreditLimit(),
				// What the user can actually spend: balance plus any credit line.
				account.getCurrentBalance().add(account.effectiveCreditLimit()), account.getStatus(),
				account.isSystemCashWallet(), account.getCreatedAt(), account.getUpdatedAt());
	}

}
