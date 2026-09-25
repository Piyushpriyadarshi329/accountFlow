package com.accountflow.transaction.export;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCsvWriterTest {

	private static Transaction transaction(String description, String amount) {
		return Transaction.builder()
			.transactionReference("TXN-1")
			.accountName("HDFC Savings")
			.transactionType(TransactionType.DEBIT)
			.direction(Direction.OUT)
			.amount(new BigDecimal(amount))
			.currency("INR")
			.balanceBefore(new BigDecimal("10000.00"))
			.balanceAfter(new BigDecimal("7000.00"))
			.category("SHOPPING")
			.description(description)
			.transactionDate(Instant.parse("2026-09-19T10:30:00Z"))
			.build();
	}

	private static String csv(Transaction... rows) throws Exception {
		StringWriter out = new StringWriter();
		TransactionCsvWriter.write(Stream.of(rows), out);
		return out.toString();
	}

	@Test
	@DisplayName("writes a header and one row per transaction")
	void writesHeaderAndRows() throws Exception {
		String output = csv(transaction("Laptop", "3000.00"));
		String[] lines = output.split("\r\n");

		assertThat(lines[0]).contains("Date").contains("Amount").contains("Balance After");
		assertThat(lines[1]).contains("TXN-1").contains("HDFC Savings").contains("2026-09-19 10:30:00");
	}

	@Test
	@DisplayName("amounts are written exactly as stored, not reformatted")
	void amountsAreExact() throws Exception {
		assertThat(csv(transaction("x", "1250.50"))).contains("\"1250.50\"");
		// A spreadsheet total has to agree with the app to the paisa.
		assertThat(csv(transaction("x", "0.05"))).contains("\"0.05\"");
	}

	@Test
	@DisplayName("a description containing a comma or quote does not break the row")
	void escapesSeparatorsAndQuotes() throws Exception {
		String output = csv(transaction("Dinner, drinks and a \"tip\"", "500.00"));

		assertThat(output).contains("\"Dinner, drinks and a \"\"tip\"\"\"");
		// Header plus exactly one data row: the comma did not split the record.
		assertThat(output.split("\r\n")).hasSize(2);
	}

	@Test
	@DisplayName("a formula in a description is neutralised rather than left executable")
	void neutralisesFormulaInjection() {
		// Excel and Sheets execute a cell beginning = + - @ on open. A description
		// is always literal text, so it must never be handed over as a formula.
		assertThat(TransactionCsvWriter.escape("=HYPERLINK(\"http://evil\",\"click\")")).startsWith("\"'=");
		assertThat(TransactionCsvWriter.escape("+1234")).startsWith("\"'+");
		assertThat(TransactionCsvWriter.escape("-1+1")).startsWith("\"'-");
		assertThat(TransactionCsvWriter.escape("@SUM(A1)")).startsWith("\"'@");
		assertThat(TransactionCsvWriter.escape("Groceries")).isEqualTo("\"Groceries\"");
	}

	@Test
	@DisplayName("a null field becomes an empty cell rather than the text null")
	void nullsBecomeEmpty() throws Exception {
		assertThat(csv(transaction(null, "100.00"))).doesNotContain("null");
	}

	@Test
	@DisplayName("starts with a byte-order mark so Excel reads it as UTF-8")
	void writesByteOrderMark() throws Exception {
		assertThat(csv(transaction("Café", "100.00")).charAt(0)).isEqualTo('﻿');
	}

}
