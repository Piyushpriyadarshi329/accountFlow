package com.accountflow.transaction.domain;

public enum TransactionType {

	CREDIT(Direction.IN), DEBIT(Direction.OUT), TRANSFER_IN(Direction.IN), TRANSFER_OUT(Direction.OUT),
	CASH_DEPOSIT(Direction.IN), CASH_WITHDRAW(Direction.OUT), REVERSAL(null);

	private final Direction direction;

	TransactionType(Direction direction) {
		this.direction = direction;
	}

	/** Null for REVERSAL, which takes the opposite direction of what it reverses. */
	public Direction direction() {
		return this.direction;
	}

}
