package com.accountflow.statement;

import java.io.IOException;
import java.io.Writer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.accountflow.transaction.domain.Transaction;

/**
 * The statement as CSV: a summary block, then the movements. Same figures as
 * the PDF, in a form a spreadsheet can total.
 */
public final class StatementCsvWriter {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(ZoneOffset.UTC);

	private StatementCsvWriter() {
	}

	public static void write(Statement statement, Writer out) throws IOException {
		out.write('﻿');

		row(out, "Account holder", statement.holderName());
		row(out, "Email", statement.holderEmail());
		row(out, "Account", statement.account().getAccountName());
		row(out, "Bank", statement.account().getBankName());
		row(out, "Account number", statement.account().getAccountNumber());
		row(out, "Type", String.valueOf(statement.account().getAccountType()));
		row(out, "Currency", statement.account().getCurrency());
		row(out, "Statement period", statement.periodLabel());
		row(out, "Generated", TIMESTAMP.format(statement.generatedAt()) + " UTC");
		out.write("\r\n");

		row(out, "Opening balance", statement.openingBalance().toPlainString());
		row(out, "Total credits", statement.totalCredits().toPlainString());
		row(out, "Total debits", statement.totalDebits().toPlainString());
		row(out, "Closing balance", statement.closingBalance().toPlainString());
		row(out, "Transactions", String.valueOf(statement.count()));
		if (statement.truncated()) {
			row(out, "Note", "Truncated at " + statement.count()
					+ " lines. Narrow the period, or use the full transaction export.");
		}
		out.write("\r\n");

		cells(out, "Date", "Reference", "Type", "Description", "Merchant", "Category", "Debit", "Credit",
				"Balance");
		for (Transaction line : statement.lines()) {
			boolean incoming = line.getDirection() == com.accountflow.transaction.domain.Direction.IN;
			cells(out, TIMESTAMP.format(line.getPostedAt()), line.getTransactionReference(),
					String.valueOf(line.getTransactionType()), line.getDescription(), line.getMerchant(),
					line.getCategory(), incoming ? "" : line.getAmount().toPlainString(),
					incoming ? line.getAmount().toPlainString() : "", line.getBalanceAfter().toPlainString());
		}
		out.flush();
	}

	private static void row(Writer out, String label, String value) throws IOException {
		cells(out, label, value);
	}

	private static void cells(Writer out, String... values) throws IOException {
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				out.write(',');
			}
			out.write(escape(values[i]));
		}
		out.write("\r\n");
	}

	/** Same formula-injection guard as the transaction export. */
	static String escape(String value) {
		if (value == null) {
			return "";
		}
		String text = value;
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		return '"' + text.replace("\"", "\"\"") + '"';
	}

}
