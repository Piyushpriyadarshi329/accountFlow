package com.accountflow.statement;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * The statement as a PDF.
 *
 * <h2>Why the text is sanitised</h2>
 *
 * The standard PDF fonts are WinAnsi-encoded. Anything outside that - a rupee
 * sign, an emoji in a description, a merchant name in Devanagari - makes
 * {@code showText} throw, which would turn one awkward transaction into a
 * failed download. Every string is therefore filtered to characters the font
 * can draw, and amounts carry the currency as a code in the column header
 * rather than as a symbol.
 */
public final class StatementPdfWriter {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
		.withZone(ZoneOffset.UTC);

	private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	private static final float MARGIN = 40;

	private static final float LINE = 14;

	/** x offset, width, right-aligned. */
	private record Column(String title, float x, float width, boolean rightAligned) {
	}

	private static final Column[] COLUMNS = { new Column("Date", 0, 92, false),
			new Column("Description", 92, 150, false), new Column("Type", 242, 78, false),
			new Column("Debit", 320, 75, true), new Column("Credit", 395, 75, true),
			new Column("Balance", 470, 85, true) };

	private StatementPdfWriter() {
	}

	public static void write(Statement statement, OutputStream out) throws IOException {
		try (PDDocument document = new PDDocument()) {
			Cursor cursor = new Cursor(document);
			cursor.newPage();

			header(cursor, statement);
			summary(cursor, statement);
			tableHeader(cursor);

			for (Transaction line : statement.lines()) {
				if (cursor.y < MARGIN + 60) {
					cursor.endPage();
					cursor.newPage();
					tableHeader(cursor);
				}
				row(cursor, line, statement.account().getCurrency());
			}

			if (statement.lines().isEmpty()) {
				cursor.y -= LINE;
				cursor.text(REGULAR, 9, MARGIN, cursor.y, "No transactions in this period.");
				cursor.y -= LINE;
			}

			closing(cursor, statement);
			cursor.endPage();
			addPageNumbers(document);
			document.save(out);
		}
	}

	private static void header(Cursor cursor, Statement statement) throws IOException {
		var account = statement.account();
		cursor.text(BOLD, 18, MARGIN, cursor.y, "Account Statement");
		cursor.y -= 22;
		cursor.text(BOLD, 11, MARGIN, cursor.y, clean(account.getAccountName()));
		cursor.y -= LINE;
		cursor.text(REGULAR, 9, MARGIN, cursor.y,
				nonEmpty(clean(statement.holderName()), clean(statement.holderEmail())));
		cursor.y -= LINE;

		String subtitle = nonEmpty(clean(account.getBankName()),
				(account.getAccountNumber() != null) ? "A/C " + clean(account.getAccountNumber()) : null,
				String.valueOf(account.getAccountType()), account.getCurrency());
		cursor.text(REGULAR, 9, MARGIN, cursor.y, subtitle);
		cursor.y -= LINE;
		cursor.text(REGULAR, 9, MARGIN, cursor.y, "Period: " + statement.periodLabel()
				+ "     Generated: " + DATE.format(statement.generatedAt()) + " UTC");
		cursor.y -= 8;
		cursor.rule();
		cursor.y -= 16;
	}

	private static void summary(Cursor cursor, Statement statement) throws IOException {
		String currency = statement.account().getCurrency();
		float top = cursor.y;
		float[] xs = { MARGIN, MARGIN + 135, MARGIN + 270, MARGIN + 405 };
		String[] labels = { "Opening balance", "Total credits", "Total debits", "Closing balance" };
		String[] values = { statement.openingBalance().toPlainString(), statement.totalCredits().toPlainString(),
				statement.totalDebits().toPlainString(), statement.closingBalance().toPlainString() };

		for (int i = 0; i < labels.length; i++) {
			cursor.text(REGULAR, 8, xs[i], top, labels[i].toUpperCase());
			cursor.text(BOLD, 12, xs[i], top - 16, currency + " " + values[i]);
		}
		cursor.y = top - 34;
		cursor.text(REGULAR, 8, MARGIN, cursor.y,
				statement.count() + " transaction(s)   -   net change " + currency + " "
						+ statement.netChange().toPlainString());
		cursor.y -= 10;
		if (statement.truncated()) {
			cursor.text(BOLD, 8, MARGIN, cursor.y,
					"Truncated at " + statement.count() + " lines - narrow the period for a complete statement.");
			cursor.y -= 10;
		}
		cursor.rule();
		cursor.y -= 16;
	}

	private static void tableHeader(Cursor cursor) throws IOException {
		for (Column column : COLUMNS) {
			cursor.cell(BOLD, 8, column, cursor.y, column.title().toUpperCase());
		}
		cursor.y -= 6;
		cursor.rule();
		cursor.y -= 12;
	}

	private static void row(Cursor cursor, Transaction line, String currency) throws IOException {
		boolean incoming = line.getDirection() == Direction.IN;
		String description = firstNonBlank(line.getDescription(), line.getMerchant(),
				String.valueOf(line.getTransactionType()));

		cursor.cell(REGULAR, 8, COLUMNS[0], cursor.y, DATE.format(line.getPostedAt()));
		cursor.cell(REGULAR, 8, COLUMNS[1], cursor.y, truncate(clean(description), 30));
		cursor.cell(REGULAR, 8, COLUMNS[2], cursor.y, String.valueOf(line.getTransactionType()));
		cursor.cell(REGULAR, 8, COLUMNS[3], cursor.y, incoming ? "" : line.getAmount().toPlainString());
		cursor.cell(REGULAR, 8, COLUMNS[4], cursor.y, incoming ? line.getAmount().toPlainString() : "");
		cursor.cell(BOLD, 8, COLUMNS[5], cursor.y, line.getBalanceAfter().toPlainString());
		cursor.y -= LINE;
	}

	private static void closing(Cursor cursor, Statement statement) throws IOException {
		cursor.y -= 4;
		cursor.rule();
		cursor.y -= 14;
		cursor.cell(BOLD, 9, COLUMNS[2], cursor.y, "Closing balance");
		cursor.cell(BOLD, 9, COLUMNS[5], cursor.y,
				statement.account().getCurrency() + " " + statement.closingBalance().toPlainString());
		cursor.y -= 24;
		cursor.text(REGULAR, 7, MARGIN, cursor.y,
				"Amounts are in " + statement.account().getCurrency()
						+ ". Periods and balances follow posting time (UTC).");
	}

	private static void addPageNumbers(PDDocument document) throws IOException {
		int total = document.getNumberOfPages();
		for (int i = 0; i < total; i++) {
			PDPage page = document.getPage(i);
			try (PDPageContentStream stream = new PDPageContentStream(document, page,
					PDPageContentStream.AppendMode.APPEND, true, true)) {
				stream.beginText();
				stream.setFont(REGULAR, 8);
				stream.newLineAtOffset(page.getMediaBox().getWidth() - MARGIN - 60, MARGIN - 10);
				stream.showText("Page " + (i + 1) + " of " + total);
				stream.endText();
			}
		}
	}

	/** Tracks the page, the content stream and the current baseline. */
	private static final class Cursor {

		private final PDDocument document;

		private PDPageContentStream stream;

		private PDPage page;

		private float y;

		Cursor(PDDocument document) {
			this.document = document;
		}

		void newPage() throws IOException {
			this.page = new PDPage(PDRectangle.A4);
			this.document.addPage(this.page);
			this.stream = new PDPageContentStream(this.document, this.page);
			this.y = this.page.getMediaBox().getHeight() - MARGIN;
		}

		void endPage() throws IOException {
			this.stream.close();
		}

		void text(PDType1Font font, float size, float x, float baseline, String value) throws IOException {
			this.stream.beginText();
			this.stream.setFont(font, size);
			this.stream.newLineAtOffset(x, baseline);
			this.stream.showText(clean(value));
			this.stream.endText();
		}

		void cell(PDType1Font font, float size, Column column, float baseline, String value) throws IOException {
			String safe = clean(value);
			float x = MARGIN + column.x();
			if (column.rightAligned()) {
				float width = font.getStringWidth(safe) / 1000 * size;
				x = MARGIN + column.x() + column.width() - width;
			}
			text(font, size, x, baseline, safe);
		}

		void rule() throws IOException {
			this.stream.setLineWidth(0.5f);
			this.stream.moveTo(MARGIN, this.y);
			this.stream.lineTo(this.page.getMediaBox().getWidth() - MARGIN, this.y);
			this.stream.stroke();
		}

	}

	/**
	 * Keeps printable ASCII and nothing else.
	 *
	 * <p>Deliberately stricter than WinAnsi. The standard-14 fonts resolve
	 * through StandardEncoding, where even a middle dot is not dependably
	 * available - an earlier version of this file silently dropped the whole
	 * header line because of one such character. A rupee sign, an emoji in a
	 * description or a name in Devanagari would do the same. Everything outside
	 * ASCII becomes a space, so an unusual description costs one row's
	 * appearance rather than the entire document.
	 */
	static String clean(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder safe = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			safe.append((c >= 32 && c <= 126) ? c : ' ');
		}
		return safe.toString().replaceAll("\\s{2,}", " ").trim();
	}

	private static String truncate(String value, int max) {
		return (value.length() <= max) ? value : value.substring(0, max - 3) + "...";
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private static String nonEmpty(String... values) {
		StringBuilder joined = new StringBuilder();
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				if (joined.length() > 0) {
					joined.append("  -  ");
				}
				joined.append(value);
			}
		}
		return joined.toString();
	}

}
