package com.accountflow.ledger;

import java.math.BigDecimal;
import java.time.Instant;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.common.exception.AccountClosedException;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.CurrencyMismatchException;
import com.accountflow.common.exception.InsufficientBalanceException;
import com.accountflow.common.money.Money;
import com.accountflow.common.util.References;
import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.repository.TransactionRepository;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only component permitted to change an account balance or append to the
 * transaction ledger.
 *
 * <h2>How the balance stays correct under concurrency</h2>
 *
 * The sufficiency check is not a read followed by a write - it is part of the
 * update's own filter, so MongoDB evaluates it atomically against the live
 * document:
 *
 * <pre>{@code
 * findAndModify(
 *     { _id, userId, status: "ACTIVE", currentBalance: { $gte: amount - creditLimit } },
 *     { $inc: { currentBalance: -amount } })
 * }</pre>
 *
 * With a balance of 10,000 and two concurrent debits of 8,000 and 7,000, the
 * first matches and leaves 2,000; the second's filter no longer matches and it
 * is rejected. No lock, no retry loop, one round trip.
 *
 * <p>A null return means the filter did not match, which is deliberately
 * ambiguous, so the cause is diagnosed with a follow-up read purely to choose
 * the right error.
 *
 * <p>{@code creditLimit} is read before the update rather than evaluated
 * server-side. The live balance - the value that actually races - is still
 * guarded atomically; a credit limit is effectively static, so using the value
 * just read is safe.
 */
@Service
public class LedgerService {

	private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

	private final MongoTemplate mongoTemplate;

	private final AccountRepository accountRepository;

	private final TransactionRepository transactionRepository;

	public LedgerService(MongoTemplate mongoTemplate, AccountRepository accountRepository,
			TransactionRepository transactionRepository) {
		this.mongoTemplate = mongoTemplate;
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
	}

	/**
	 * Applies one posting and appends the matching ledger entry. Joins the
	 * caller's transaction so a transfer can post both legs atomically.
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public Transaction post(PostingCommand command) {
		Account account = this.accountRepository.findByIdAndUserId(command.accountId(), command.userId())
			.orElseThrow(AccountNotFoundException::new);

		if (!account.getStatus().acceptsPostings()) {
			throw new AccountClosedException(account.getStatus());
		}
		if (command.currency() != null && !command.currency().equalsIgnoreCase(account.getCurrency())) {
			throw new CurrencyMismatchException(account.getCurrency(), command.currency());
		}

		BigDecimal amount = Money.validateAmount(command.amount(), account.getCurrency());
		Direction direction = command.direction();

		Account before = applyBalanceChange(account, direction, amount);
		if (before == null) {
			throw diagnoseFailure(command);
		}

		BigDecimal balanceBefore = before.getCurrentBalance();
		BigDecimal balanceAfter = balanceBefore.add(direction.signed(amount));
		Instant now = Instant.now();

		Transaction transaction = this.transactionRepository.save(Transaction.builder()
			.transactionReference(References.transaction())
			.userId(command.userId())
			.accountId(account.getId())
			.accountName(account.getAccountName())
			.transactionType(command.type())
			.direction(direction)
			.amount(amount)
			.currency(account.getCurrency())
			.balanceBefore(balanceBefore)
			.balanceAfter(balanceAfter)
			.category(command.category())
			.description(command.description())
			.merchant(command.merchant())
			.transactionDate((command.transactionDate() != null) ? command.transactionDate() : now)
			.postedAt(now)
			.transferId(command.transferId())
			.transferReference(command.transferReference())
			.reversalOf(command.reversalOf())
			.idempotencyKey(command.idempotencyKey())
			.metadata(command.metadata())
			.build());

		log.info("Posted {} {} {} on account {} ({} -> {})", direction, amount, account.getCurrency(),
				account.getId(), balanceBefore, balanceAfter);
		return transaction;
	}

	/**
	 * The conditional atomic update. Returns the pre-update document, which is
	 * the only trustworthy source for {@code balanceBefore}: reading the balance
	 * separately would reintroduce the race this eliminates.
	 */
	private Account applyBalanceChange(Account account, Direction direction, BigDecimal amount) {
		Criteria criteria = Criteria.where("id")
			.is(account.getId())
			.and("userId")
			.is(account.getUserId())
			.and("status")
			.is(AccountStatus.ACTIVE);

		if (direction == Direction.OUT) {
			// balanceAfter >= -creditLimit  <=>  currentBalance >= amount - creditLimit
			BigDecimal minimumBalance = amount.subtract(account.effectiveCreditLimit());
			criteria = criteria.and("currentBalance").gte(new Decimal128(minimumBalance));
		}

		Update update = new Update().inc("currentBalance", new Decimal128(direction.signed(amount)))
			.set("updatedAt", Instant.now())
			.inc("version", 1);

		return this.mongoTemplate.findAndModify(Query.query(criteria), update,
				FindAndModifyOptions.options().returnNew(false), Account.class);
	}

	/** Only reached on failure, to turn "filter did not match" into a useful error. */
	private RuntimeException diagnoseFailure(PostingCommand command) {
		Account current = this.accountRepository.findByIdAndUserId(command.accountId(), command.userId())
			.orElse(null);
		if (current == null) {
			return new AccountNotFoundException();
		}
		if (!current.getStatus().acceptsPostings()) {
			return new AccountClosedException(current.getStatus());
		}
		return new InsufficientBalanceException();
	}

}
