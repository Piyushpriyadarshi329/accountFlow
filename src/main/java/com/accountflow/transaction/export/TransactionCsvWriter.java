package com.accountflow.transaction.export;

import java.io.IOException;
import java.io.Writer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import com.accountflow.transaction.domain.Transaction;

/**
 * Writes a ledger export as CSV, one row at a time from a cursor.
 */
public final class TransactionCsvWriter {

	private static final String[] HEADERS = { "Date", "Reference", "Account", "Type", "Direction", "Amount",
			"Currency", "Balance After", "Category", "Description", "Merchant", "Status" };

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(ZoneOffset.UTC);

	private TransactionCsvWriter() {
	}

	public static long write(Stream<Transaction> transactions, Writer out) throws IOException {
		// Byte-order mark so Excel reads the file as UTF-8; without it, a rupee
		// sign or an accented merchant name arrives as mojibake.
		out.write('﻿');
		writeRow(out, HEADERS);

		long[] count = { 0 };
		try {
			transactions.forEach((transaction) -> {
				try {
					writeRow(out, new String[] { DATE.format(transaction.getTransactionDate()),
							transaction.getTransactionReference(), transaction.getAccountName(),
							String.valueOf(transaction.getTransactionType()),
							String.valueOf(transaction.getDirection()),
							// Amounts stay exactly as stored. Formatting them here
							// would mean a spreadsheet total disagreeing with the app.
							transaction.getAmount().toPlainString(), transaction.getCurrency(),
							transaction.getBalanceAfter().toPlainString(), transaction.getCategory(),
							transaction.getDescription(), transaction.getMerchant(),
							String.valueOf(transaction.getStatus()) });
					count[0]++;
				}
				catch (IOException ex) {
					throw new UncheckedIOWrapper(ex);
				}
			});
		}
		catch (UncheckedIOWrapper ex) {
			throw ex.getCause();
		}
		out.flush();
		return count[0];
	}

	private static void writeRow(Writer out, String[] values) throws IOException {
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				out.write(',');
			}
			out.write(escape(values[i]));
		}
		out.write("\r\n");
	}

	/**
	 * Quotes the field, and neutralises formula injection.
	 *
	 * <p>A description a user typed as {@code =HYPERLINK(...)} is executed by
	 * Excel and Sheets when the file is opened - a stored payload that runs on
	 * whoever opens the export. Prefixing with an apostrophe makes the cell
	 * literal text, which is what a transaction description always is.
	 */
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

	private static final class UncheckedIOWrapper extends RuntimeException {

		UncheckedIOWrapper(IOException cause) {
			super(cause);
		}

		@Override
		public synchronized IOException getCause() {
			return (IOException) super.getCause();
		}

	}

}
