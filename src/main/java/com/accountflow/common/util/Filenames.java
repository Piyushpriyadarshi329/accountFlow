package com.accountflow.common.util;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Builds download file names.
 *
 * <p>A name carries who the data belongs to and the period it covers, so a
 * folder of exports is still readable months later and two users' files never
 * collide.
 *
 * <p>The "user" part is the local part of the email address. There is no
 * separate username in this system, and the email is unique where a display
 * name is not - this database already holds several people called "Piyush
 * Priyadarshi", whose statements would otherwise overwrite each other.
 */
public final class Filenames {

	private Filenames() {
	}

	/** Lowercase, hyphenated, filesystem- and header-safe. */
	public static String slug(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return slug.isEmpty() ? "unknown" : slug;
	}

	/** "piyush-demo" from "Piyush.Demo@example.com". */
	public static String userSlug(String email) {
		if (email == null || email.isBlank()) {
			return "user";
		}
		return slug(email.split("@")[0]);
	}

    public static String period(LocalDate from, LocalDate to) {
		return (from != null ? from.toString() : "start") + "-to-" + (to != null ? to.toString() : "today");
	}

	/** e.g. {@code piyush-demo_hdfc-savings_statement_2026-09-01-to-2026-09-30} */
	public static String build(String email, String subject, String kind, LocalDate from, LocalDate to) {
		StringBuilder name = new StringBuilder(userSlug(email));
		if (subject != null && !subject.isBlank()) {
			name.append('_').append(slug(subject));
		}
		return name.append('_').append(kind).append('_').append(period(from, to)).toString();
	}

}
