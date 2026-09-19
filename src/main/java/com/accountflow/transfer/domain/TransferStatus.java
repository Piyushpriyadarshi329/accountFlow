package com.accountflow.transfer.domain;

public enum TransferStatus {

	/**
	 * A transfer only becomes visible once both legs have committed, so PENDING
	 * is never observed in the current design. It is kept for the reconciliation
	 * path a future non-transactional (saga) implementation would need.
	 */
	PENDING, COMPLETED, FAILED, REVERSED

}
