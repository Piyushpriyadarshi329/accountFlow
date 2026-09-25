package com.accountflow.statement;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.domain.AccountType;
import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StatementPdfWriterTest {

	private static Statement statement(List<Transaction> lines) {
		Account account = Account.builder()
			.id("acct-1")
			.accountName("HDFC Savings")
			.bankName("HDFC Bank")
			.accountType(AccountType.SAVINGS)
			.currency("INR")
			.openingBalance(new BigDecimal("1000.00"))
			.currentBalance(new BigDecimal("1000.00"))
			.creditLimit(BigDecimal.ZERO)
			.status(AccountStatus.ACTIVE)
			.build();
		return new Statement(account, "Piyush Priyadarshi", "piyush.demo@example.com", LocalDate.of(2026, 9, 1),
				LocalDate.of(2026, 9, 30), Instant.now(),
				new BigDecimal("1000.00"), new BigDecimal("1500.00"), new BigDecimal("500.00"), BigDecimal.ZERO,
				lines, false);
	}

	private static Transaction line(String description) {
		return Transaction.builder()
			.transactionReference("TXN-1")
			.transactionType(TransactionType.CREDIT)
			.direction(Direction.IN)
			.amount(new BigDecimal("500.00"))
			.currency("INR")
			.balanceBefore(new BigDecimal("1000.00"))
			.balanceAfter(new BigDecimal("1500.00"))
			.description(description)
			.postedAt(Instant.parse("2026-09-10T10:00:00Z"))
			.transactionDate(Instant.parse("2026-09-10T10:00:00Z"))
			.build();
	}

	private static byte[] render(Statement statement) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StatementPdfWriter.write(statement, out);
		return out.toByteArray();
	}

	@Test
	@DisplayName("produces a real PDF")
	void producesAPdf() throws Exception {
		byte[] pdf = render(statement(List.of(line("Salary"))));

		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		assertThat(pdf.length).isGreaterThan(800);
	}

	@Test
	@DisplayName("non-ASCII text does not break the document")
	void survivesNonAsciiText() {
		// The standard-14 fonts resolve through StandardEncoding, where a rupee
		// sign, an emoji or Devanagari has no glyph. Previously such a character
		// silently dropped the whole line it appeared on.
		assertThatCode(() -> render(statement(List.of(line("Café ₹500 — 🎉 किराया")))))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("an empty period still renders a statement")
	void rendersEmptyPeriod() throws Exception {
		assertThat(render(statement(List.of()))).isNotEmpty();
	}

	@Test
	@DisplayName("clean keeps printable ASCII and replaces the rest")
	void cleanKeepsAsciiOnly() {
		assertThat(StatementPdfWriter.clean("HDFC Bank - SAVINGS")).isEqualTo("HDFC Bank - SAVINGS");
		assertThat(StatementPdfWriter.clean("₹500")).isEqualTo("500");
		assertThat(StatementPdfWriter.clean("a·b")).isEqualTo("a b");
		assertThat(StatementPdfWriter.clean(null)).isEmpty();
	}

}
