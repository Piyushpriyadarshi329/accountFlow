package com.accountflow.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.domain.AccountType;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transaction.repository.TransactionSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatementServiceTest {

	private static final LocalDate FROM = LocalDate.of(2026, 9, 1);

	private static final LocalDate TO = LocalDate.of(2026, 9, 30);

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TransactionSearchRepository searchRepository;

	@Mock
	private com.accountflow.user.repository.UserRepository userRepository;

	private StatementService service;

	@BeforeEach
	void setUp() {
		this.service = new StatementService(this.accountRepository, this.searchRepository, this.userRepository);
		given(this.userRepository.findById("user-1")).willReturn(Optional.of(com.accountflow.user.domain.User
			.builder()
			.id("user-1")
			.firstName("Piyush")
			.lastName("Priyadarshi")
			.email("piyush.demo@example.com")
			.build()));
		given(this.accountRepository.findByIdAndUserId("acct-1", "user-1")).willReturn(Optional.of(Account.builder()
			.id("acct-1")
			.userId("user-1")
			.accountName("HDFC Savings")
			.accountType(AccountType.SAVINGS)
			.currency("INR")
			.openingBalance(new BigDecimal("1000.00"))
			.currentBalance(new BigDecimal("1000.00"))
			.creditLimit(BigDecimal.ZERO)
			.status(AccountStatus.ACTIVE)
			.build()));
		given(this.searchRepository.lastPostingBefore(anyString(), anyString(), any())).willReturn(Optional.empty());
		given(this.searchRepository.postingsBetween(anyString(), anyString(), any(), any(), anyInt()))
			.willReturn(List.of());
	}

	private static Transaction line(String amount, Direction direction, String before, String after, int day) {
		return Transaction.builder()
			.transactionReference("TXN-" + day)
			.transactionType(direction == Direction.IN ? TransactionType.CREDIT : TransactionType.DEBIT)
			.direction(direction)
			.amount(new BigDecimal(amount))
			.currency("INR")
			.balanceBefore(new BigDecimal(before))
			.balanceAfter(new BigDecimal(after))
			.postedAt(Instant.parse("2026-09-%02dT10:00:00Z".formatted(day)))
			.transactionDate(Instant.parse("2026-09-%02dT10:00:00Z".formatted(day)))
			.build();
	}

	private void givenLines(Transaction... lines) {
		given(this.searchRepository.postingsBetween(anyString(), anyString(), any(), any(), anyInt()))
			.willReturn(List.of(lines));
	}

	@Test
	@DisplayName("opening comes from the first line's own balanceBefore")
	void openingFromFirstLine() {
		givenLines(line("500.00", Direction.OUT, "5000.00", "4500.00", 5),
				line("2000.00", Direction.IN, "4500.00", "6500.00", 12));

		Statement statement = this.service.build("user-1", "acct-1", FROM, TO);

		assertThat(statement.openingBalance()).isEqualByComparingTo("5000.00");
		assertThat(statement.closingBalance()).isEqualByComparingTo("6500.00");
	}

	@Test
	@DisplayName("opening plus credits minus debits equals closing - the statement reconciles")
	void statementReconciles() {
		givenLines(line("500.00", Direction.OUT, "5000.00", "4500.00", 5),
				line("2000.00", Direction.IN, "4500.00", "6500.00", 12),
				line("250.50", Direction.OUT, "6500.00", "6249.50", 20));

		Statement statement = this.service.build("user-1", "acct-1", FROM, TO);

		assertThat(statement.totalCredits()).isEqualByComparingTo("2000.00");
		assertThat(statement.totalDebits()).isEqualByComparingTo("750.50");
		assertThat(statement.openingBalance().add(statement.totalCredits()).subtract(statement.totalDebits()))
			.isEqualByComparingTo(statement.closingBalance());
		assertThat(statement.netChange()).isEqualByComparingTo("1249.50");
	}

	@Test
	@DisplayName("with no activity in the period, opening carries over from the last prior posting")
	void openingCarriesOverWhenPeriodIsEmpty() {
		given(this.searchRepository.lastPostingBefore(anyString(), anyString(), any()))
			.willReturn(Optional.of(line("100.00", Direction.IN, "7900.00", "8000.00", 1)));

		Statement statement = this.service.build("user-1", "acct-1", FROM, TO);

		// A quiet month still has a balance, and closing must equal opening.
		assertThat(statement.openingBalance()).isEqualByComparingTo("8000.00");
		assertThat(statement.closingBalance()).isEqualByComparingTo("8000.00");
		assertThat(statement.count()).isZero();
		assertThat(statement.netChange()).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("a brand-new account falls back to its opening balance")
	void fallsBackToAccountOpeningBalance() {
		Statement statement = this.service.build("user-1", "acct-1", FROM, TO);

		assertThat(statement.openingBalance()).isEqualByComparingTo("1000.00");
		assertThat(statement.closingBalance()).isEqualByComparingTo("1000.00");
	}

	@Test
	@DisplayName("an all-time statement starts from the account's opening balance")
	void allTimeStartsAtAccountOpening() {
		givenLines(line("500.00", Direction.IN, "1000.00", "1500.00", 3));

		Statement statement = this.service.build("user-1", "acct-1", null, null);

		assertThat(statement.openingBalance()).isEqualByComparingTo("1000.00");
		assertThat(statement.periodLabel()).isEqualTo("All time");
	}

	@Test
	@DisplayName("another user's account is not readable")
	void refusesForeignAccount() {
		assertThatThrownBy(() -> this.service.build("someone-else", "acct-1", FROM, TO))
			.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	@DisplayName("an inverted period is rejected")
	void rejectsInvertedPeriod() {
		assertThatThrownBy(() -> this.service.build("user-1", "acct-1", TO, FROM))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_DATE_RANGE);
	}

	@Test
	@DisplayName("the file name carries the user, the account and the period")
	void fileStemDescribesTheStatement() {
		// Two people here share a display name, so the email local part is what
		// keeps one person's statement from overwriting another's.
		assertThat(this.service.build("user-1", "acct-1", FROM, TO).fileStem())
			.isEqualTo("piyush-demo_hdfc-savings_statement_2026-09-01-to-2026-09-30");
	}

	@Test
	@DisplayName("the statement names the account holder")
	void namesTheAccountHolder() {
		Statement statement = this.service.build("user-1", "acct-1", FROM, TO);

		assertThat(statement.holderName()).isEqualTo("Piyush Priyadarshi");
		assertThat(statement.holderEmail()).isEqualTo("piyush.demo@example.com");
	}

}
