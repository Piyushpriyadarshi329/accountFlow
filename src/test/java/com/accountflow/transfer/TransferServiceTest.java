package com.accountflow.transfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.domain.AccountType;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.account.service.AccountService;
import com.accountflow.common.exception.AccountClosedException;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.CurrencyMismatchException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.exception.SameAccountTransferException;
import com.accountflow.common.idempotency.IdempotencyService;
import com.accountflow.common.idempotency.MoneyOperationExecutor;
import com.accountflow.ledger.LedgerService;
import com.accountflow.ledger.PostingCommand;
import com.accountflow.transaction.domain.Direction;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transfer.domain.Transfer;
import com.accountflow.transfer.domain.TransferKind;
import com.accountflow.transfer.domain.TransferStatus;
import com.accountflow.transfer.dto.BankCashTransferRequest;
import com.accountflow.transfer.dto.TransferRequest;
import com.accountflow.transfer.repository.TransferRepository;
import com.accountflow.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

	@Mock
	private LedgerService ledgerService;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountService accountService;

	@Mock
	private TransferRepository transferRepository;

	@Mock
	private IdempotencyService idempotencyService;

	@Mock
	private MoneyOperationExecutor executor;

	private TransferService service;

	@BeforeEach
	void setUp() {
		this.service = new TransferService(this.ledgerService, this.accountRepository, this.accountService,
				this.transferRepository, this.idempotencyService, this.executor);
		// Run the supplied work directly; the transactional envelope is exercised
		// by the integration test against a real replica set.
		given(this.executor.execute(anyString(), anyString(), anyString(), anyString(), any(), any()))
			.willAnswer((invocation) -> {
				Supplier<?> work = invocation.getArgument(4);
				return work.get();
			});
		given(this.transferRepository.insert(any(Transfer.class))).willAnswer((i) -> i.getArgument(0));
		given(this.ledgerService.post(any(PostingCommand.class)))
			.willAnswer((i) -> Transaction.builder().id("txn").build());
	}

	private static Account account(String id, String currency, AccountStatus status, AccountType type) {
		return Account.builder()
			.id(id)
			.userId("user-1")
			.accountName("Account " + id)
			.accountType(type)
			.currency(currency)
			.currentBalance(new BigDecimal("50000.00"))
			.openingBalance(new BigDecimal("50000.00"))
			.creditLimit(BigDecimal.ZERO)
			.status(status)
			.build();
	}

	private void givenAccounts(Account... accounts) {
		for (Account account : accounts) {
			given(this.accountRepository.findByIdAndUserId(account.getId(), "user-1"))
				.willReturn(Optional.of(account));
		}
	}

	@Test
	@DisplayName("a transfer to the same account is refused")
	void refusesSelfTransfer() {
		TransferRequest request = new TransferRequest("aaa", "aaa", new BigDecimal("100"), "self");

		assertThatThrownBy(() -> this.service.transfer("user-1", request, "KEY"))
			.isInstanceOf(SameAccountTransferException.class);
		verify(this.ledgerService, never()).post(any());
	}

	@Test
	@DisplayName("an Idempotency-Key is required")
	void requiresIdempotencyKey() {
		TransferRequest request = new TransferRequest("aaa", "bbb", new BigDecimal("100"), "x");

		assertThatThrownBy(() -> this.service.transfer("user-1", request, null))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
	}

	@Test
	@DisplayName("a transfer across currencies is refused rather than silently converted")
	void refusesCrossCurrency() {
		givenAccounts(account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS),
				account("bbb", "USD", AccountStatus.ACTIVE, AccountType.SAVINGS));

		assertThatThrownBy(() -> this.service.transfer("user-1",
				new TransferRequest("aaa", "bbb", new BigDecimal("100"), "x"), "KEY"))
			.isInstanceOf(CurrencyMismatchException.class);
		verify(this.ledgerService, never()).post(any());
	}

	@Test
	@DisplayName("a closed account on either side stops the transfer before any posting")
	void refusesClosedAccounts() {
		givenAccounts(account("aaa", "INR", AccountStatus.CLOSED, AccountType.SAVINGS),
				account("bbb", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS));

		assertThatThrownBy(() -> this.service.transfer("user-1",
				new TransferRequest("aaa", "bbb", new BigDecimal("100"), "x"), "KEY"))
			.isInstanceOf(AccountClosedException.class);

		givenAccounts(account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS),
				account("bbb", "INR", AccountStatus.INACTIVE, AccountType.SAVINGS));

		assertThatThrownBy(() -> this.service.transfer("user-1",
				new TransferRequest("aaa", "bbb", new BigDecimal("100"), "x"), "KEY"))
			.isInstanceOf(AccountClosedException.class);
		verify(this.ledgerService, never()).post(any());
	}

	@Test
	@DisplayName("another user's account reads as not found")
	void refusesForeignAccount() {
		given(this.accountRepository.findByIdAndUserId("aaa", "user-1")).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.transfer("user-1",
				new TransferRequest("aaa", "bbb", new BigDecimal("100"), "x"), "KEY"))
			.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	@DisplayName("both legs are posted in account-id order, whichever way the money flows")
	void postsLegsInDeterministicOrder() {
		// Money flows bbb -> aaa, but aaa sorts first, so aaa is touched first.
		givenAccounts(account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS),
				account("bbb", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS));

		this.service.transfer("user-1", new TransferRequest("bbb", "aaa", new BigDecimal("100"), "x"), "KEY");

		ArgumentCaptor<PostingCommand> captor = ArgumentCaptor.forClass(PostingCommand.class);
		verify(this.ledgerService, org.mockito.Mockito.times(2)).post(captor.capture());
		List<PostingCommand> posted = captor.getAllValues();

		assertThat(posted.get(0).accountId()).isEqualTo("aaa");
		assertThat(posted.get(0).direction()).isEqualTo(Direction.IN);
		assertThat(posted.get(1).accountId()).isEqualTo("bbb");
		assertThat(posted.get(1).direction()).isEqualTo(Direction.OUT);
	}

	@Test
	@DisplayName("the reverse direction touches the accounts in the same order")
	void orderIsStableAcrossDirections() {
		givenAccounts(account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS),
				account("bbb", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS));

		this.service.transfer("user-1", new TransferRequest("aaa", "bbb", new BigDecimal("100"), "x"), "KEY");

		ArgumentCaptor<PostingCommand> captor = ArgumentCaptor.forClass(PostingCommand.class);
		verify(this.ledgerService, org.mockito.Mockito.times(2)).post(captor.capture());

		// Same first account as the opposite-direction transfer above: that is
		// what prevents two crossing transfers from conflicting.
		assertThat(captor.getAllValues().get(0).accountId()).isEqualTo("aaa");
		assertThat(captor.getAllValues().get(0).direction()).isEqualTo(Direction.OUT);
	}

	@Test
	@DisplayName("both legs share one transfer reference, linking the pair")
	void legsShareATransferReference() {
		givenAccounts(account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS),
				account("bbb", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS));

		this.service.transfer("user-1", new TransferRequest("aaa", "bbb", new BigDecimal("100"), "x"), "KEY");

		ArgumentCaptor<PostingCommand> captor = ArgumentCaptor.forClass(PostingCommand.class);
		verify(this.ledgerService, org.mockito.Mockito.times(2)).post(captor.capture());
		List<PostingCommand> posted = captor.getAllValues();

		assertThat(posted.get(0).transferReference()).isNotBlank()
			.isEqualTo(posted.get(1).transferReference());
		assertThat(posted.get(0).transferId()).isEqualTo(posted.get(1).transferId());
	}

	@Test
	@DisplayName("bank-to-cash resolves the cash wallet as the destination")
	void bankToCashTargetsTheCashWallet() {
		Account bank = account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS);
		Account cash = account("zzz", "INR", AccountStatus.ACTIVE, AccountType.CASH);
		givenAccounts(bank, cash);
		given(this.accountService.getOrCreateCashWallet("user-1")).willReturn(cash);

		this.service.bankToCash("user-1", new BankCashTransferRequest("aaa", new BigDecimal("1000"), "atm"), "KEY");

		ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
		verify(this.transferRepository).insert(captor.capture());
		Transfer transfer = captor.getValue();

		assertThat(transfer.getKind()).isEqualTo(TransferKind.BANK_TO_CASH);
		assertThat(transfer.getSourceAccountId()).isEqualTo("aaa");
		assertThat(transfer.getDestinationAccountId()).isEqualTo("zzz");
		assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
	}

	@Test
	@DisplayName("cash-to-bank resolves the cash wallet as the source")
	void cashToBankTakesFromTheCashWallet() {
		Account bank = account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS);
		Account cash = account("zzz", "INR", AccountStatus.ACTIVE, AccountType.CASH);
		givenAccounts(bank, cash);
		given(this.accountService.getOrCreateCashWallet("user-1")).willReturn(cash);

		this.service.cashToBank("user-1", new BankCashTransferRequest("aaa", new BigDecimal("1000"), "deposit"),
				"KEY");

		ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
		verify(this.transferRepository).insert(captor.capture());

		assertThat(captor.getValue().getKind()).isEqualTo(TransferKind.CASH_TO_BANK);
		assertThat(captor.getValue().getSourceAccountId()).isEqualTo("zzz");
		assertThat(captor.getValue().getDestinationAccountId()).isEqualTo("aaa");
	}

	@Test
	@DisplayName("an over-precise amount is refused before anything is posted")
	void refusesOverPreciseAmount() {
		givenAccounts(account("aaa", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS),
				account("bbb", "INR", AccountStatus.ACTIVE, AccountType.SAVINGS));

		assertThatThrownBy(() -> this.service.transfer("user-1",
				new TransferRequest("aaa", "bbb", new BigDecimal("100.999"), "x"), "KEY"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_AMOUNT);
		verify(this.ledgerService, never()).post(any());
	}

}
