package com.accountflow.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.accountflow.account.domain.Account;
import com.accountflow.transaction.domain.Transaction;

/**
 * A bank-style statement for one account over one period.
 *
 * <p>Opening and closing balances are read off the ledger's own
 * {@code balanceAfter} chain rather than recomputed by summing, so the figures
 * on the statement are the ones that were actually recorded at the time.
 */
public record Statement(Account account, String holderName, String holderEmail, LocalDate from, LocalDate to,
		Instant generatedAt, BigDecimal openingBalance, BigDecimal closingBalance, BigDecimal totalCredits,
		BigDecimal totalDebits, List<Transaction> lines, boolean truncated) {

	public int count() {
		return this.lines.size();
	}

	public BigDecimal netChange() {
		return this.closingBalance.subtract(this.openingBalance);
	}

	public String periodLabel() {
		if (this.from == null && this.to == null) {
			return "All time";
		}
		return (this.from != null ? this.from.toString() : "opening") + " to "
				+ (this.to != null ? this.to.toString() : "today");
	}

	/** e.g. {@code piyush-demo_hdfc-savings_statement_2026-09-01-to-2026-09-30} */
	public String fileStem() {
		return com.accountflow.common.util.Filenames.build(this.holderEmail, this.account.getAccountName(),
				"statement", this.from, this.to);
	}

}
