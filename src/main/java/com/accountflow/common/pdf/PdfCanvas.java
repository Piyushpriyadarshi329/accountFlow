package com.accountflow.common.pdf;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * A minimal page/cursor abstraction over PDFBox, shared by the documents this
 * application produces.
 */
public final class PdfCanvas implements AutoCloseable {

	public static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	public static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	public static final float MARGIN = 40;

	public static final float LINE = 14;

	/** A table column: offset from the margin, width, and alignment. */
	public record Column(String title, float x, float width, boolean rightAligned) {
	}

	private final PDDocument document;

	private PDPageContentStream stream;

	private PDPage page;

	private float y;

	public PdfCanvas(PDDocument document) {
		this.document = document;
	}

	public float y() {
		return this.y;
	}

	public void moveDown(float amount) {
		this.y -= amount;
	}

	public boolean needsNewPage() {
		return this.y < MARGIN + 60;
	}

	public void newPage() throws IOException {
		this.page = new PDPage(PDRectangle.A4);
		this.document.addPage(this.page);
		this.stream = new PDPageContentStream(this.document, this.page);
		this.y = this.page.getMediaBox().getHeight() - MARGIN;
	}

	public void endPage() throws IOException {
		if (this.stream != null) {
			this.stream.close();
			this.stream = null;
		}
	}

	public void text(PDType1Font font, float size, float x, String value) throws IOException {
		textAt(font, size, x, this.y, value);
	}

	public void textAt(PDType1Font font, float size, float x, float baseline, String value) throws IOException {
		this.stream.beginText();
		this.stream.setFont(font, size);
		this.stream.newLineAtOffset(x, baseline);
		this.stream.showText(clean(value));
		this.stream.endText();
	}

	public void cell(PDType1Font font, float size, Column column, String value) throws IOException {
		String safe = clean(value);
		float x = MARGIN + column.x();
		if (column.rightAligned()) {
			x = MARGIN + column.x() + column.width() - font.getStringWidth(safe) / 1000 * size;
		}
		textAt(font, size, x, this.y, safe);
	}

	public void rule() throws IOException {
		this.stream.setLineWidth(0.5f);
		this.stream.moveTo(MARGIN, this.y);
		this.stream.lineTo(this.page.getMediaBox().getWidth() - MARGIN, this.y);
		this.stream.stroke();
	}

	/** Stamps "Page n of m" once the total is known. */
	public static void addPageNumbers(PDDocument document) throws IOException {
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

	/**
	 * Keeps printable ASCII and nothing else.
	 *
	 * <p>The standard-14 fonts resolve through StandardEncoding, where even a
	 * middle dot is not dependably available - that silently dropped a whole
	 * header line before this existed. A rupee sign, an emoji in a description
	 * or a name in Devanagari would do the same. Everything else becomes a
	 * space, so an unusual value costs one row's appearance, not the document.
	 */
	public static String clean(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder safe = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			safe.append((c >= 32 && c <= 126) ? c : ' ');
		}
		return safe.toString().replaceAll("\\s{2,}", " ").trim();
	}

	public static String truncate(String value, int max) {
		String safe = clean(value);
		return (safe.length() <= max) ? safe : safe.substring(0, Math.max(1, max - 3)) + "...";
	}

	public static String join(String... values) {
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

	@Override
	public void close() throws IOException {
		endPage();
	}

}
