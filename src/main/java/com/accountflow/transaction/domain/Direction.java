package com.accountflow.transaction.domain;

import java.math.BigDecimal;

/**
 * Which way money moved. Amounts are always stored positive; this carries the
 * sign, so a negative amount can never be mistaken for the opposite operation.
 */
public enum Direction {

	IN, OUT;

	public BigDecimal signed(BigDecimal amount) {
		return (this == IN) ? amount : amount.negate();
	}

	public Direction opposite() {
		return (this == IN) ? OUT : IN;
	}

}
