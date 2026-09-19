package com.accountflow.common.mongo;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries a MongoDB transaction that aborted for a transient reason.
 *
 * <p>When two transactions modify the same document concurrently, MongoDB
 * aborts one with a {@code WriteConflict} tagged
 * {@code TransientTransactionError}. The driver does <em>not</em> retry these:
 * the application must, or a losing request surfaces as a 500 instead of the
 * real outcome.
 *
 * <p>Retrying is safe here because the aborted transaction committed nothing -
 * including its idempotency claim - so the retry re-reads the now-current
 * balance and either succeeds or fails with the correct business error.
 *
 * <p>{@code UnknownTransactionCommitResult} is also retried: the commit may
 * have succeeded, and the idempotency claim makes a second attempt harmless.
 */
public final class TransientTransactionRetry {

	private static final Logger log = LoggerFactory.getLogger(TransientTransactionRetry.class);

	/**
	 * Sized by measurement, not guesswork: with 20 concurrent debits on one
	 * account a 4-attempt budget rejected most of them with "busy", even though
	 * funds were available. Each conflict resolves in milliseconds, so a longer
	 * budget converts those needless rejections into correct outcomes.
	 */
	private static final int DEFAULT_MAX_ATTEMPTS = 12;

	private static final long BASE_BACKOFF_MILLIS = 10;

	/** Caps exponential growth so the worst case stays around a second. */
	private static final long MAX_BACKOFF_MILLIS = 150;

	private TransientTransactionRetry() {
	}

	public static <T> T execute(Supplier<T> action) {
		return execute(DEFAULT_MAX_ATTEMPTS, action);
	}

	public static <T> T execute(int maxAttempts, Supplier<T> action) {
		for (int attempt = 1;; attempt++) {
			try {
				return action.get();
			}
			catch (RuntimeException ex) {
				if (attempt >= maxAttempts || !isRetryable(ex)) {
					throw ex;
				}
				log.debug("Transient transaction conflict on attempt {} of {}; retrying", attempt, maxAttempts);
				backoff(attempt);
			}
		}
	}

	/** Walks the cause chain: Spring wraps the driver exception several layers deep. */
	public static boolean isRetryable(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof MongoException mongoException
					&& (mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
							|| mongoException.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
				return true;
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return false;
	}

	/** Exponential backoff with jitter, so retries do not collide again in lockstep. */
	private static void backoff(int attempt) {
		long ceiling = Math.min(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS * (1L << Math.min(attempt - 1, 20)));
		try {
			Thread.sleep(ThreadLocalRandom.current().nextLong(1, ceiling + 1));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while retrying a transaction", ex);
		}
	}

}
