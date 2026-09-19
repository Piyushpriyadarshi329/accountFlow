package com.accountflow.transaction.mapper;

import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.dto.TransactionResponse;

public final class TransactionMapper {

	private TransactionMapper() {
	}

	public static TransactionResponse toResponse(Transaction transaction) {
		return new TransactionResponse(transaction.getId(), transaction.getTransactionReference(),
				transaction.getAccountId(), transaction.getAccountName(), transaction.getTransactionType(),
				transaction.getDirection(), transaction.getAmount(), transaction.getCurrency(),
				transaction.getBalanceBefore(), transaction.getBalanceAfter(), transaction.getCategory(),
				transaction.getDescription(), transaction.getMerchant(), transaction.getTransactionDate(),
				transaction.getPostedAt(), transaction.getStatus(), transaction.getTransferReference(),
				transaction.getReversalOf(), transaction.getMetadata());
	}

}
