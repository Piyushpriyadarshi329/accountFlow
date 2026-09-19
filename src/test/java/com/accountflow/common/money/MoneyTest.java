package com.accountflow.common.money;

import java.math.BigDecimal;

import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

	@Test
	@DisplayName("accepts an amount at the currency's scale and normalizes it")
	void acceptsAmountAtCurrencyScale() {
		assertThat(Money.validateAmount(new BigDecimal("1500"), "INR")).isEqualByComparingTo("1500.00");
		assertThat(Money.validateAmount(new BigDecimal("1500"), "INR").scale()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "-0.01" })
	@DisplayName("rejects zero and negative amounts")
	void rejectsNonPositiveAmounts(String amount) {
		assertThatThrownBy(() -> Money.validateAmount(new BigDecimal(amount), "INR"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_AMOUNT);
	}

	@Test
	@DisplayName("rejects rather than rounds an amount with too much precision")
	void rejectsExcessivePrecision() {
		// 10.999 must not silently become 11.00 - that loses the user's money.
		assertThatThrownBy(() -> Money.validateAmount(new BigDecimal("10.999"), "INR"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("precision");
	}

	@Test
	@DisplayName("honours per-currency scale")
	void honoursPerCurrencyScale() {
		assertThat(Money.scaleOf("JPY")).isZero();
		assertThat(Money.scaleOf("INR")).isEqualTo(2);
		assertThat(Money.scaleOf("BHD")).isEqualTo(3);
		assertThatThrownBy(() -> Money.validateAmount(new BigDecimal("100.50"), "JPY"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("rejects an unknown currency")
	void rejectsUnknownCurrency() {
		assertThatThrownBy(() -> Money.scaleOf("XYZ")).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("null amount is rejected, not treated as zero")
	void rejectsNull() {
		assertThatThrownBy(() -> Money.validateAmount(null, "INR")).isInstanceOf(BusinessException.class);
	}

}
