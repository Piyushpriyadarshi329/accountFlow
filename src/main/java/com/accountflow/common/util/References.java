package com.accountflow.common.util;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Human-readable, time-sortable business references. The leading timestamp in
 * base-36 keeps references in creation order when sorted as strings, which is
 * convenient in logs and support tools.
 */
public final class References {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

	private References() {
	}

	public static String transaction() {
		return "TXN-" + generate();
	}

	public static String transfer() {
		return "TRF-" + generate();
	}

	private static String generate() {
		StringBuilder suffix = new StringBuilder(8);
		for (int i = 0; i < 8; i++) {
			suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return Long.toString(Instant.now().toEpochMilli(), 36).toUpperCase() + "-" + suffix;
	}

}
