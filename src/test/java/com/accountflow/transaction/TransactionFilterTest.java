package com.accountflow.transaction;

import java.time.Instant;
import java.time.LocalDate;

import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.transaction.dto.TransactionFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionFilterTest {

	private static TransactionFilter dates(LocalDate from, LocalDate to) {
		return new TransactionFilter(null, null, null, from, to, null, null, null, null);
	}

	@Test
	@DisplayName("the end date is inclusive - it runs to the end of that day")
	void endDateIsInclusive() {
		TransactionFilter filter = dates(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

		assertThat(filter.fromInstant()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
		// Exclusive upper bound at the start of 1 October, so a transaction at
		// 23:59 on the 30th is inside the range instead of silently dropped.
		assertThat(filter.toInstantExclusive()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
	}

	@Test
	@DisplayName("a single-day range covers that whole day")
	void singleDayRange() {
		TransactionFilter filter = dates(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 19));

		assertThat(filter.fromInstant()).isEqualTo(Instant.parse("2026-09-19T00:00:00Z"));
		assertThat(filter.toInstantExclusive()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
	}

	@Test
	@DisplayName("an inverted range is rejected rather than quietly returning nothing")
	void rejectsInvertedRange() {
		assertThatThrownBy(() -> dates(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_DATE_RANGE);
	}

	@Test
	@DisplayName("open-ended ranges are allowed in both directions")
	void openEndedRanges() {
		assertThat(dates(LocalDate.of(2026, 9, 1), null).toInstantExclusive()).isNull();
		assertThat(dates(null, LocalDate.of(2026, 9, 30)).fromInstant()).isNull();
		assertThat(dates(null, null).isEmpty()).isTrue();
	}

}
