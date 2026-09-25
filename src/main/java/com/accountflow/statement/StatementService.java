package com.accountflow.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.accountflow.account.domain.Account;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.repository.TransactionSearchRepository;
import com.accountflow.common.exception.UserNotFoundException;
import com.accountflow.user.domain.User;
import com.accountflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Builds an account statement.
 *
 * <p>The period is measured by <em>posting</em> time, not the business
 * {@code transactionDate}. A statement's whole point is that opening plus the
 * listed movements equals closing, and that only holds along the order the
 * balances were actually applied. A backdated transaction would otherwise
 * appear in a period whose closing balance does not account for it.
 */
@Service
public class StatementService {

	/**
	 * A statement is a document to read, not a bulk export. Past this many lines
	 * the caller is told to narrow the period or use the CSV export instead,
	 * rather than being handed a thousand-page PDF.
	 */
	private static final int MAX_LINES = 2000;

	private final AccountRepository accountRepository;

	private final TransactionSearchRepository searchRepository;

	private final UserRepository userRepository;

	public StatementService(AccountRepository accountRepository, TransactionSearchRepository searchRepository,
			UserRepository userRepository) {
		this.accountRepository = accountRepository;
		this.searchRepository = searchRepository;
		this.userRepository = userRepository;
	}

	public Statement build(String userId, String accountId, LocalDate from, LocalDate to) {
		if (from != null && to != null && to.isBefore(from)) {
			throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
					"The end date cannot be before the start date");
		}
		Account account = this.accountRepository.findByIdAndUserId(accountId, userId)
			.orElseThrow(AccountNotFoundException::new);
		User holder = this.userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

		Instant fromInstant = (from != null) ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
		Instant toExclusive = (to != null) ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

		List<Transaction> lines = this.searchRepository.postingsBetween(userId, accountId, fromInstant, toExclusive,
				MAX_LINES + 1);
		boolean truncated = lines.size() > MAX_LINES;
		if (truncated) {
			lines = lines.subList(0, MAX_LINES);
		}

		BigDecimal opening = openingBalance(userId, accountId, account, fromInstant, lines);
		BigDecimal closing = lines.isEmpty() ? opening : lines.get(lines.size() - 1).getBalanceAfter();

		// Seeded at the currency's scale so a period with no credits reads
		// "0.00" rather than a bare "0" next to every other figure.
		BigDecimal credits = com.accountflow.common.money.Money.zero(account.getCurrency());
		BigDecimal debits = com.accountflow.common.money.Money.zero(account.getCurrency());
		for (Transaction line : lines) {
			if (line.getDirection() == Direction.IN) {
				credits = credits.add(line.getAmount());
			}
			else {
				debits = debits.add(line.getAmount());
			}
		}

		return new Statement(account, holder.fullName(), holder.getEmail(), from, to, Instant.now(), opening,
				closing, credits, debits, lines, truncated);
	}

	/**
	 * The balance going into the period.
	 *
	 * <p>Taken from the first line's own {@code balanceBefore} when there is
	 * one - that is exactly what the account stood at. Otherwise from the last
	 * posting before the period, and failing that from the account's opening
	 * balance, which is where a brand-new account starts.
	 */
	private BigDecimal openingBalance(String userId, String accountId, Account account, Instant fromInstant,
			List<Transaction> lines) {
		if (!lines.isEmpty()) {
			return lines.get(0).getBalanceBefore();
		}
		if (fromInstant == null) {
			return account.getOpeningBalance();
		}
		return this.searchRepository.lastPostingBefore(userId, accountId, fromInstant)
			.map(Transaction::getBalanceAfter)
			.orElse(account.getOpeningBalance());
	}

}
