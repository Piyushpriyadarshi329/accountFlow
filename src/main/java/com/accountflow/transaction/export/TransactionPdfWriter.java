package com.accountflow.transaction.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.accountflow.common.pdf.PdfCanvas;
import com.accountflow.common.pdf.PdfCanvas.Column;
import com.accountflow.common.util.Filenames;
import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * The ledger as a PDF: every account's movements over a period, in one
 * document.
 *
 * <p>Unlike an account statement there is no single opening and closing figure,
 * because the rows span accounts. What is shown instead are the totals in and
 * out, and each row's balance for its own account.
 */
public final class TransactionPdfWriter {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yy HH:mm")
		.withZone(ZoneOffset.UTC);

	private static final Column[] COLUMNS = { new Column("Date", 0, 82, false),
			new Column("Account", 82, 92, false), new Column("Description", 174, 128, false),
			new Column("Debit", 302, 76, true), new Column("Credit", 378, 76, true),
			new Column("Balance", 454, 84, true) };

	private TransactionPdfWriter() {
	}

	public static void write(List<Transaction> lines, String holderName, String holderEmail, LocalDate from,
			LocalDate to, boolean truncated, OutputStream out) throws IOException {
		try (PDDocument document = new PDDocument(); PdfCanvas canvas = new PdfCanvas(document)) {
			canvas.newPage();
			header(canvas, holderName, holderEmail, from, to);
			summary(canvas, lines, truncated);
			tableHeader(canvas);

			for (Transaction line : lines) {
				if (canvas.needsNewPage()) {
					canvas.endPage();
					canvas.newPage();
					tableHeader(canvas);
				}
				row(canvas, line);
			}

			if (lines.isEmpty()) {
				canvas.moveDown(PdfCanvas.LINE);
				canvas.text(PdfCanvas.REGULAR, 9, PdfCanvas.MARGIN, "No transactions in this period.");
			}

			canvas.endPage();
			PdfCanvas.addPageNumbers(document);
			document.save(out);
		}
	}

	private static void header(PdfCanvas canvas, String holderName, String holderEmail, LocalDate from,
			LocalDate to) throws IOException {
		canvas.text(PdfCanvas.BOLD, 18, PdfCanvas.MARGIN, "Transaction Ledger");
		canvas.moveDown(22);
		canvas.text(PdfCanvas.BOLD, 11, PdfCanvas.MARGIN, PdfCanvas.join(holderName, holderEmail));
		canvas.moveDown(PdfCanvas.LINE);
		canvas.text(PdfCanvas.REGULAR, 9, PdfCanvas.MARGIN,
				"Period: " + Filenames.period(from, to).replace("-to-", " to ") + "     Generated: "
						+ DATE.format(Instant.now()) + " UTC");
		canvas.moveDown(8);
		canvas.rule();
		canvas.moveDown(16);
	}

	private static void summary(PdfCanvas canvas, List<Transaction> lines, boolean truncated) throws IOException {
		BigDecimal credits = BigDecimal.ZERO;
		BigDecimal debits = BigDecimal.ZERO;
		String currency = lines.isEmpty() ? "" : lines.get(0).getCurrency();
		for (Transaction line : lines) {
			if (line.getDirection() == Direction.IN) {
				credits = credits.add(line.getAmount());
			}
			else {
				debits = debits.add(line.getAmount());
			}
		}

		float top = canvas.y();
		float[] xs = { PdfCanvas.MARGIN, PdfCanvas.MARGIN + 180, PdfCanvas.MARGIN + 360 };
		String[] labels = { "TOTAL CREDITS", "TOTAL DEBITS", "TRANSACTIONS" };
		String[] values = { currency + " " + credits.toPlainString(), currency + " " + debits.toPlainString(),
				String.valueOf(lines.size()) };
		for (int i = 0; i < labels.length; i++) {
			canvas.textAt(PdfCanvas.REGULAR, 8, xs[i], top, labels[i]);
			canvas.textAt(PdfCanvas.BOLD, 12, xs[i], top - 16, values[i]);
		}
		canvas.moveDown(34);
		if (truncated) {
			canvas.text(PdfCanvas.BOLD, 8, PdfCanvas.MARGIN,
					"Truncated at " + lines.size() + " rows - narrow the period, or use the CSV export.");
			canvas.moveDown(12);
		}
		canvas.rule();
		canvas.moveDown(16);
	}

	private static void tableHeader(PdfCanvas canvas) throws IOException {
		for (Column column : COLUMNS) {
			canvas.cell(PdfCanvas.BOLD, 8, column, column.title().toUpperCase());
		}
		canvas.moveDown(6);
		canvas.rule();
		canvas.moveDown(12);
	}

	private static void row(PdfCanvas canvas, Transaction line) throws IOException {
		boolean incoming = line.getDirection() == Direction.IN;
		String description = (line.getDescription() != null && !line.getDescription().isBlank())
				? line.getDescription()
				: (line.getMerchant() != null ? line.getMerchant() : String.valueOf(line.getTransactionType()));

		canvas.cell(PdfCanvas.REGULAR, 8, COLUMNS[0], DATE.format(line.getPostedAt()));
		canvas.cell(PdfCanvas.REGULAR, 8, COLUMNS[1], PdfCanvas.truncate(line.getAccountName(), 18));
		canvas.cell(PdfCanvas.REGULAR, 8, COLUMNS[2], PdfCanvas.truncate(description, 26));
		canvas.cell(PdfCanvas.REGULAR, 8, COLUMNS[3], incoming ? "" : line.getAmount().toPlainString());
		canvas.cell(PdfCanvas.REGULAR, 8, COLUMNS[4], incoming ? line.getAmount().toPlainString() : "");
		canvas.cell(PdfCanvas.BOLD, 8, COLUMNS[5], line.getBalanceAfter().toPlainString());
		canvas.moveDown(PdfCanvas.LINE);
	}

}
