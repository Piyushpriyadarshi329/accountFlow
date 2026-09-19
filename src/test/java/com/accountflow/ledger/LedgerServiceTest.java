package com.accountflow.ledger;

import java.math.BigDecimal;
import java.util.Optional;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.domain.AccountType;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.common.exception.AccountClosedException;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.CurrencyMismatchException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.exception.InsufficientBalanceException;
import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transaction.repository.TransactionRepository;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TransactionRepository transactionRepository;

	private LedgerService ledger() {
		return new LedgerService(this.mongoTemplate, this.accountRepository, this.transactionRepository);
	}

	private static Account account(BigDecimal balance, AccountStatus status, BigDecimal creditLimit) {
		return Account.builder()
			.id("acct-1")
			.userId("user-1")
			.accountName("HDFC Savings")
			.accountType(AccountType.SAVINGS)
			.currency("INR")
			.openingBalance(new BigDecimal("0.00"))
			.currentBalance(balance)
			.creditLimit(creditLimit)
			.status(status)
			.build();
	}

	private void givenAccount(Account account) {
		given(this.accountRepository.findByIdAndUserId("acct-1", "user-1")).willReturn(Optional.of(account));
		given(this.transactionRepository.save(any(Transaction.class))).willAnswer((i) -> i.getArgument(0));
	}

	private static PostingCommand command(TransactionType type, String amount) {
		return PostingCommand.builder()
			.userId("user-1")
			.accountId("acct-1")
			.type(type)
			.amount(new BigDecimal(amount))
			.description("test")
			.build();
	}

	@Test
	@DisplayName("credit adds to the balance and records before/after from the atomic update")
	void creditRecordsBalances() {
		Account before = account(new BigDecimal("10000.00"), AccountStatus.ACTIVE, BigDecimal.ZERO);
		givenAccount(before);
		given(this.mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Account.class)))
			.willReturn(before);

		Transaction posted = ledger().post(command(TransactionType.CREDIT, "5000.00"));

		assertThat(posted.getDirection()).isEqualTo(Direction.IN);
		assertThat(posted.getBalanceBefore()).isEqualByComparingTo("10000.00");
		assertThat(posted.getBalanceAfter()).isEqualByComparingTo("15000.00");
		assertThat(posted.getAmount()).isEqualByComparingTo("5000.00");
	}

	@Test
	@DisplayName("debit subtracts from the balance")
	void debitRecordsBalances() {
		Account before = account(new BigDecimal("15000.00"), AccountStatus.ACTIVE, BigDecimal.ZERO);
		givenAccount(before);
		given(this.mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Account.class)))
			.willReturn(before);

		Transaction posted = ledger().post(command(TransactionType.DEBIT, "3000.00"));

		assertThat(posted.getDirection()).isEqualTo(Direction.OUT);
		assertThat(posted.getBalanceBefore()).isEqualByComparingTo("15000.00");
		assertThat(posted.getBalanceAfter()).isEqualByComparingTo("12000.00");
	}

	@Test
	@DisplayName("the debit guard is part of the update filter, not a prior read")
	void debitGuardIsInTheQuery() {
		Account before = account(new BigDecimal("10000.00"), AccountStatus.ACTIVE, BigDecimal.ZERO);
		givenAccount(before);
		given(this.mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Account.class)))
			.willReturn(before);

		ledger().post(command(TransactionType.DEBIT, "3000.00"));

		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
		verify(this.mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
				any(FindAndModifyOptions.class), eq(Account.class));

		// Inspect the document structure rather than toJson(): the Criteria still
		// holds the raw enum at this point, and Spring Data only converts it to
		// its stored form later, inside QueryMapper.
		org.bson.Document query = queryCaptor.getValue().getQueryObject();
		assertThat(query.get("status")).isEqualTo(AccountStatus.ACTIVE);
		assertThat(query.get("userId")).isEqualTo("user-1");
		assertThat(query.get("currentBalance", org.bson.Document.class).get("$gte"))
			.isInstanceOf(Decimal128.class);
		// threshold = amount - creditLimit = 3000 - 0
		assertThat(((Decimal128) query.get("currentBalance", org.bson.Document.class).get("$gte")).bigDecimalValue())
			.isEqualByComparingTo("3000.00");

		org.bson.Document increment = updateCaptor.getValue()
			.getUpdateObject()
			.get("$inc", org.bson.Document.class);
		assertThat(((Decimal128) increment.get("currentBalance")).bigDecimalValue())
			.isEqualByComparingTo("-3000.00");
	}

	@Test
	@DisplayName("a credit limit lowers the threshold, so a card may go negative within it")
	void creditLimitLowersTheThreshold() {
		Account before = account(new BigDecimal("0.00"), AccountStatus.ACTIVE, new BigDecimal("50000.00"));
		before.setAccountType(AccountType.CREDIT_CARD);
		givenAccount(before);
		given(this.mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Account.class)))
			.willReturn(before);

		Transaction posted = ledger().post(command(TransactionType.DEBIT, "20000.00"));

		assertThat(posted.getBalanceAfter()).isEqualByComparingTo("-20000.00");

		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		verify(this.mongoTemplate).findAndModify(queryCaptor.capture(), any(Update.class),
				any(FindAndModifyOptions.class), eq(Account.class));
		// threshold = amount - creditLimit = 20000 - 50000 = -30000
		Object gte = queryCaptor.getValue().getQueryObject().get("currentBalance", org.bson.Document.class).get("$gte");
		assertThat(((Decimal128) gte).bigDecimalValue()).isEqualByComparingTo("-30000.00");
	}

	@Test
	@DisplayName("a non-matching filter becomes INSUFFICIENT_BALANCE when the account is otherwise fine")
	void reportsInsufficientBalance() {
		Account current = account(new BigDecimal("2000.00"), AccountStatus.ACTIVE, BigDecimal.ZERO);
		givenAccount(current);
		given(this.mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Account.class)))
			.willReturn(null);

		assertThatThrownBy(() -> ledger().post(command(TransactionType.DEBIT, "7000.00")))
			.isInstanceOf(InsufficientBalanceException.class);
		verify(this.transactionRepository, never()).save(any());
	}

	@Test
	@DisplayName("a closed account is rejected before any balance change is attempted")
	void rejectsClosedAccount() {
		givenAccount(account(new BigDecimal("10000.00"), AccountStatus.CLOSED, BigDecimal.ZERO));

		assertThatThrownBy(() -> ledger().post(command(TransactionType.CREDIT, "100.00")))
			.isInstanceOf(AccountClosedException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.ACCOUNT_CLOSED);
		verify(this.mongoTemplate, never()).findAndModify(any(), any(), any(), eq(Account.class));
	}

	@Test
	@DisplayName("an inactive account is rejected with its own code")
	void rejectsInactiveAccount() {
		givenAccount(account(new BigDecimal("10000.00"), AccountStatus.INACTIVE, BigDecimal.ZERO));

		assertThatThrownBy(() -> ledger().post(command(TransactionType.CREDIT, "100.00")))
			.isInstanceOf(AccountClosedException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
	}

	@Test
	@DisplayName("another user's account is reported as not found, not as forbidden")
	void foreignAccountIsNotFound() {
		given(this.accountRepository.findByIdAndUserId("acct-1", "user-1")).willReturn(Optional.empty());

		assertThatThrownBy(() -> ledger().post(command(TransactionType.CREDIT, "100.00")))
			.isInstanceOf(AccountNotFoundException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
	}

	@Test
	@DisplayName("a currency that does not match the account is rejected")
	void rejectsCurrencyMismatch() {
		givenAccount(account(new BigDecimal("10000.00"), AccountStatus.ACTIVE, BigDecimal.ZERO));

		PostingCommand command = PostingCommand.builder()
			.userId("user-1")
			.accountId("acct-1")
			.type(TransactionType.CREDIT)
			.amount(new BigDecimal("100.00"))
			.currency("USD")
			.build();

		assertThatThrownBy(() -> ledger().post(command)).isInstanceOf(CurrencyMismatchException.class);
	}

	@Test
	@DisplayName("a zero or over-precise amount never reaches the database")
	void rejectsInvalidAmounts() {
		givenAccount(account(new BigDecimal("10000.00"), AccountStatus.ACTIVE, BigDecimal.ZERO));

		assertThatThrownBy(() -> ledger().post(command(TransactionType.CREDIT, "0")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_AMOUNT);
		assertThatThrownBy(() -> ledger().post(command(TransactionType.CREDIT, "10.999")))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_AMOUNT);

		verify(this.mongoTemplate, never()).findAndModify(any(), any(), any(), eq(Account.class));
	}

	@Test
	@DisplayName("the ledger entry snapshots the account name so a later rename cannot rewrite history")
	void snapshotsAccountName() {
		Account before = account(new BigDecimal("10000.00"), AccountStatus.ACTIVE, BigDecimal.ZERO);
		givenAccount(before);
		given(this.mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Account.class)))
			.willReturn(before);

		assertThat(ledger().post(command(TransactionType.CREDIT, "100.00")).getAccountName())
			.isEqualTo("HDFC Savings");
	}

}
