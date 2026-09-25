package com.accountflow.common.util;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenamesTest {

	private static final LocalDate FROM = LocalDate.of(2026, 9, 1);

	private static final LocalDate TO = LocalDate.of(2026, 9, 30);

	@Test
	@DisplayName("carries user, subject and period")
	void buildsAReadableName() {
		assertThat(Filenames.build("Piyush.Demo@example.com", "HDFC Savings", "statement", FROM, TO))
			.isEqualTo("piyush-demo_hdfc-savings_statement_2026-09-01-to-2026-09-30");
	}

	@Test
	@DisplayName("omits the subject when there is none")
	void omitsAbsentSubject() {
		assertThat(Filenames.build("asha@example.com", null, "transactions", FROM, TO))
			.isEqualTo("asha_transactions_2026-09-01-to-2026-09-30");
	}

	@Test
	@DisplayName("open-ended periods read sensibly")
	void openEndedPeriods() {
		assertThat(Filenames.period(null, null)).isEqualTo("start-to-today");
		assertThat(Filenames.period(FROM, null)).isEqualTo("2026-09-01-to-today");
		assertThat(Filenames.period(null, TO)).isEqualTo("start-to-2026-09-30");
	}

	@Test
	@DisplayName("strips anything that would upset a filesystem or a header")
	void sanitisesAggressively() {
		// A quote or newline here would break the Content-Disposition header.
		assertThat(Filenames.slug("HDFC \"Savings\"/2024\n")).isEqualTo("hdfc-savings-2024");
		assertThat(Filenames.slug("  ")).isEqualTo("unknown");
		assertThat(Filenames.slug(null)).isEqualTo("unknown");
		assertThat(Filenames.userSlug(null)).isEqualTo("user");
	}

}
