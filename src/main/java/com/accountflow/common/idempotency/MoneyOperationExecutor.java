package com.accountflow.common.idempotency;

import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a money-moving operation and its idempotency claim inside one MongoDB
 * transaction.
 *
 * <p>Both must commit together. Were the claim written separately, a crash in
 * between would either record a success for money that never moved, or
 * permanently block a legitimate retry.
 *
 * <p>This lives in its own bean so the caller can catch the duplicate-key
 * failure <em>outside</em> the transaction boundary - catching it inside would
 * leave the transaction already marked rollback-only.
 *
 * <p>Generic over the result so a single posting, a two-leg transfer and a cash
 * operation all share one transactional envelope.
 */
@Component
public class MoneyOperationExecutor {

	private final IdempotencyService idempotencyService;

	public MoneyOperationExecutor(IdempotencyService idempotencyService) {
		this.idempotencyService = idempotencyService;
	}

	/**
	 * @param resourceId extracts the id recorded against the claim, so a retry can
	 * be answered with the original resource instead of repeating the work
	 */
	@Transactional
	public <T> T execute(String userId, String endpoint, String idempotencyKey, String fingerprint,
			Supplier<T> work, Function<T, String> resourceId) {
		IdempotencyRecord claim = this.idempotencyService.claim(userId, endpoint, idempotencyKey, fingerprint);
		T result = work.get();
		this.idempotencyService.complete(claim.getId(), resourceId.apply(result));
		return result;
	}

}
