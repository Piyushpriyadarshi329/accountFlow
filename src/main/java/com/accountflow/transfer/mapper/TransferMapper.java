package com.accountflow.transfer.mapper;

import com.accountflow.transfer.domain.Transfer;
import com.accountflow.transfer.dto.TransferResponse;

public final class TransferMapper {

	private TransferMapper() {
	}

	public static TransferResponse toResponse(Transfer transfer) {
		return new TransferResponse(transfer.getId(), transfer.getTransferReference(), transfer.getSourceAccountId(),
				transfer.getSourceAccountName(), transfer.getDestinationAccountId(),
				transfer.getDestinationAccountName(), transfer.getAmount(), transfer.getCurrency(),
				transfer.getKind(), transfer.getStatus(), transfer.getDebitTransactionId(),
				transfer.getCreditTransactionId(), transfer.getDescription(), transfer.getCreatedAt(),
				transfer.getCompletedAt());
	}

}
