package com.accountflow.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.transaction.domain.TransactionType;

/**
 * Criteria for searching and exporting transactions.
 *
 * <p>Dates are plain calendar days rather than instants: a user picking "1st to
 * 30th" means those days whole. {@code to} is therefore inclusive - the range
 * ends at the first moment of the following day, so a transaction at 23:59 on
 * the end date is included rather than silently dropped.
 *
 * <p>Days are interpreted in UTC because that is how transactions are stored.
 * Presenting them in the user's zone is the client's job.
 */
public record TransactionFilter(String accountId, TransactionType transactionType, String category, LocalDate from,
		LocalDate to, BigDecimal minAmount, BigDecimal maxAmount, String merchant, String query) {

	public TransactionFilter {
		if (from != null && to != null && to.isBefore(from)) {
			throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
					"The end date cannot be before the start date");
		}
	}

	public Instant fromInstant() {
		return (this.from != null) ? this.from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
	}

	/** Exclusive upper bound: start of the day after {@code to}. */
	public Instant toInstantExclusive() {
		return (this.to != null) ? this.to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
	}

	public boolean isEmpty() {
		return this.accountId == null && this.transactionType == null && this.category == null && this.from == null
				&& this.to == null && this.minAmount == null && this.maxAmount == null && this.merchant == null
				&& this.query == null;
	}

}
