package com.accountflow.common.mongo;

import java.util.concurrent.atomic.AtomicInteger;

import com.mongodb.MongoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransientTransactionRetryTest {

	private static DataIntegrityViolationException writeConflict() {
		MongoException mongoException = new MongoException(112, "Write conflict during plan execution");
		mongoException.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
		// Spring wraps the driver exception, which is why the check walks causes.
		return new DataIntegrityViolationException("wrapped", mongoException);
	}

	@Test
	@DisplayName("recognises a transient error nested inside Spring's wrapper")
	void recognisesWrappedTransientError() {
		assertThat(TransientTransactionRetry.isRetryable(writeConflict())).isTrue();
		assertThat(TransientTransactionRetry.isRetryable(new DuplicateKeyException("dupe"))).isFalse();
		assertThat(TransientTransactionRetry.isRetryable(new IllegalStateException("boom"))).isFalse();
	}

	@Test
	@DisplayName("retries a write conflict and returns the later success")
	void retriesUntilSuccess() {
		AtomicInteger attempts = new AtomicInteger();

		String result = TransientTransactionRetry.execute(4, () -> {
			if (attempts.incrementAndGet() < 3) {
				throw writeConflict();
			}
			return "posted";
		});

		assertThat(result).isEqualTo("posted");
		assertThat(attempts.get()).isEqualTo(3);
	}

	@Test
	@DisplayName("does not retry a business failure, so a real error surfaces immediately")
	void doesNotRetryNonTransientFailures() {
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> TransientTransactionRetry.execute(4, () -> {
			attempts.incrementAndGet();
			throw new DuplicateKeyException("dupe");
		})).isInstanceOf(DuplicateKeyException.class);

		assertThat(attempts.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("gives up after the attempt budget and rethrows")
	void givesUpAfterBudget() {
		AtomicInteger attempts = new AtomicInteger();

		assertThatThrownBy(() -> TransientTransactionRetry.execute(3, () -> {
			attempts.incrementAndGet();
			throw writeConflict();
		})).isInstanceOf(DataIntegrityViolationException.class);

		assertThat(attempts.get()).isEqualTo(3);
	}

}
