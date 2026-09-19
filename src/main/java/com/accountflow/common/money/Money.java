package com.accountflow.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;

/**
 * Money rules in one place. Every amount entering the system passes through
 * here, so precision and sign are guaranteed before anything touches a balance.
 *
 * <p>Amounts whose scale exceeds the currency's are <em>rejected</em>, not
 * rounded: silently turning 10.999 into 11.00 loses a user's money without
 * telling them.
 */
public final class Money {

	/** Banker's rounding, for computed values only (never for user input). */
	public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

	private Money() {
	}

	public static int scaleOf(String currencyCode) {
		try {
			int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
			return (digits < 0) ? 2 : digits;
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported currency: " + currencyCode);
		}
	}

	public static BigDecimal zero(String currencyCode) {
		return BigDecimal.ZERO.setScale(scaleOf(currencyCode));
	}

	/** Validates sign and precision, returning the amount at the currency's scale. */
	public static BigDecimal validateAmount(BigDecimal amount, String currencyCode) {
		if (amount == null) {
			throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Amount is required");
		}
		if (amount.signum() <= 0) {
			throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
		}
		int scale = scaleOf(currencyCode);
		if (amount.stripTrailingZeros().scale() > scale) {
			throw new BusinessException(ErrorCode.INVALID_AMOUNT,
					"Amount exceeds the precision allowed for " + currencyCode + " (" + scale + " decimal places)");
		}
		return amount.setScale(scale, ROUNDING);
	}

	/** Rounds a computed value (interest, aggregate, split) to the currency scale. */
	public static BigDecimal round(BigDecimal value, String currencyCode) {
		return value.setScale(scaleOf(currencyCode), ROUNDING);
	}

}
