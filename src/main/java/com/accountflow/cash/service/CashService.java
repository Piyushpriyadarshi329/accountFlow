package com.accountflow.cash.service;

import com.accountflow.account.domain.Account;
import com.accountflow.account.service.AccountService;
import com.accountflow.cash.dto.CashOperationRequest;
import com.accountflow.cash.dto.CashResponse;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transaction.dto.PostTransactionRequest;
import com.accountflow.transaction.dto.TransactionResponse;
import com.accountflow.transaction.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Physical cash, presented as its own resource.
 *
 * <p>There is no separate cash ledger: the wallet is an Account of type CASH,
 * so deposits and withdrawals are ordinary postings and inherit the balance
 * guard, idempotency, conflict retry and audit trail unchanged. An insufficient
 * cash withdrawal is refused by exactly the same mechanism that protects a bank
 * account, with no special case anywhere.
 */
@Service
public class CashService {

	private static final String CASH_CATEGORY = "CASH";

	private final AccountService accountService;

	private final TransactionService transactionService;

	public CashService(AccountService accountService, TransactionService transactionService) {
		this.accountService = accountService;
		this.transactionService = transactionService;
	}

	public CashResponse summary(String userId) {
		Account cash = this.accountService.getOrCreateCashWallet(userId);
		return new CashResponse(cash.getId(), cash.getCurrency(), cash.getCurrentBalance(), cash.getStatus(),
				cash.getUpdatedAt());
	}

	/** Physical cash coming in from outside the system. */
	public TransactionResponse deposit(String userId, CashOperationRequest request, String idempotencyKey) {
		return post(userId, TransactionType.CASH_DEPOSIT, request, idempotencyKey);
	}

	/** Physical cash leaving the system - spent, not moved to a bank account. */
	public TransactionResponse withdraw(String userId, CashOperationRequest request, String idempotencyKey) {
		return post(userId, TransactionType.CASH_WITHDRAW, request, idempotencyKey);
	}

	public Page<TransactionResponse> transactions(String userId, Pageable pageable) {
		Account cash = this.accountService.getOrCreateCashWallet(userId);
		return this.transactionService.listForAccount(userId, cash.getId(), pageable);
	}

	private TransactionResponse post(String userId, TransactionType type, CashOperationRequest request,
			String idempotencyKey) {
		Account cash = this.accountService.getOrCreateCashWallet(userId);
		PostTransactionRequest posting = new PostTransactionRequest(request.amount(), CASH_CATEGORY,
				request.description(), null, null, request.metadata());
		return this.transactionService.post(userId, cash.getId(), type, posting, idempotencyKey);
	}

}
