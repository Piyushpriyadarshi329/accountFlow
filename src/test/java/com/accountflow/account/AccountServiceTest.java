package com.accountflow.account;

import java.math.BigDecimal;
import java.util.Optional;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.domain.AccountType;
import com.accountflow.account.dto.CreateAccountRequest;
import com.accountflow.account.dto.UpdateAccountRequest;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.account.service.AccountService;
import com.accountflow.common.exception.AccountNotEmptyException;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.DuplicateAccountNumberException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private AccountService accountService;

	private void echoSave() {
		given(this.accountRepository.save(any(Account.class))).willAnswer((i) -> {
			Account a = i.getArgument(0);
			if (a.getId() == null) {
				a.setId("acct-1");
			}
			return a;
		});
	}

	private static CreateAccountRequest request(AccountType type, String opening, String creditLimit) {
		return new CreateAccountRequest("HDFC Savings", "HDFC", type, "INR", "123456",
				(opening != null) ? new BigDecimal(opening) : null,
				(creditLimit != null) ? new BigDecimal(creditLimit) : null);
	}

	@Test
	@DisplayName("a new account starts with currentBalance equal to openingBalance")
	void currentBalanceStartsAtOpeningBalance() {
		echoSave();

		var response = this.accountService.create("user-1", request(AccountType.SAVINGS, "10000", null));

		assertThat(response.openingBalance()).isEqualByComparingTo("10000.00");
		assertThat(response.currentBalance()).isEqualByComparingTo("10000.00");
		assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	@DisplayName("availableBalance includes the credit line")
	void availableBalanceIncludesCreditLine() {
		echoSave();

		var response = this.accountService.create("user-1", request(AccountType.CREDIT_CARD, "0", "50000"));

		assertThat(response.currentBalance()).isEqualByComparingTo("0.00");
		assertThat(response.availableBalance()).isEqualByComparingTo("50000.00");
	}

	@Test
	@DisplayName("a credit limit is rejected on anything but a credit card")
	void creditLimitOnlyOnCreditCard() {
		assertThatThrownBy(() -> this.accountService.create("user-1", request(AccountType.SAVINGS, "0", "50000")))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("CREDIT_CARD");
	}

	@Test
	@DisplayName("the cash account cannot be created by hand")
	void cashAccountCannotBeCreatedManually() {
		assertThatThrownBy(() -> this.accountService.create("user-1", request(AccountType.CASH, "0", null)))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("automatically");
	}

	@Test
	@DisplayName("an account number already used by this user is rejected")
	void rejectsDuplicateAccountNumber() {
		given(this.accountRepository.existsByUserIdAndAccountNumber("user-1", "123456")).willReturn(true);

		assertThatThrownBy(() -> this.accountService.create("user-1", request(AccountType.SAVINGS, "0", null)))
			.isInstanceOf(DuplicateAccountNumberException.class);
	}

	@Test
	@DisplayName("another user's account reads as not found")
	void foreignAccountIsNotFound() {
		given(this.accountRepository.findByIdAndUserId("acct-1", "user-1")).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.accountService.get("user-1", "acct-1"))
			.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	@DisplayName("an account holding money cannot be closed")
	void cannotCloseAccountWithBalance() {
		Account account = Account.builder()
			.id("acct-1")
			.userId("user-1")
			.currency("INR")
			.accountType(AccountType.SAVINGS)
			.currentBalance(new BigDecimal("2500.00"))
			.openingBalance(BigDecimal.ZERO)
			.creditLimit(BigDecimal.ZERO)
			.status(AccountStatus.ACTIVE)
			.build();
		given(this.accountRepository.findByIdAndUserId("acct-1", "user-1")).willReturn(Optional.of(account));

		assertThatThrownBy(() -> this.accountService.updateStatus("user-1", "acct-1", AccountStatus.CLOSED))
			.isInstanceOf(AccountNotEmptyException.class)
			.hasMessageContaining("2500.00");
	}

	@Test
	@DisplayName("updating an account cannot change its currency, type or balance")
	void updateLeavesFinancialFieldsAlone() {
		Account account = Account.builder()
			.id("acct-1")
			.userId("user-1")
			.accountName("Old")
			.currency("INR")
			.accountType(AccountType.SAVINGS)
			.currentBalance(new BigDecimal("5000.00"))
			.openingBalance(new BigDecimal("5000.00"))
			.creditLimit(BigDecimal.ZERO)
			.status(AccountStatus.ACTIVE)
			.build();
		given(this.accountRepository.findByIdAndUserId("acct-1", "user-1")).willReturn(Optional.of(account));
		echoSave();

		var response = this.accountService.update("user-1", "acct-1",
				new UpdateAccountRequest("Renamed", "SBI", "999"));

		assertThat(response.accountName()).isEqualTo("Renamed");
		assertThat(account.getCurrency()).isEqualTo("INR");
		assertThat(account.getAccountType()).isEqualTo(AccountType.SAVINGS);
		assertThat(account.getCurrentBalance()).isEqualByComparingTo("5000.00");
	}

	@Test
	@DisplayName("the cash wallet is created on first use and reused thereafter")
	void cashWalletIsLazyAndSingular() {
		given(this.accountRepository.findByUserIdAndAccountType("user-1", AccountType.CASH))
			.willReturn(Optional.empty());
		echoSave();

		Account created = this.accountService.getOrCreateCashWallet("user-1");
		assertThat(created.getAccountType()).isEqualTo(AccountType.CASH);
		assertThat(created.isSystemCashWallet()).isTrue();
		assertThat(created.getCurrentBalance()).isEqualByComparingTo("0.00");

		given(this.accountRepository.findByUserIdAndAccountType("user-1", AccountType.CASH))
			.willReturn(Optional.of(created));
		assertThat(this.accountService.getOrCreateCashWallet("user-1")).isSameAs(created);
	}

	@Test
	@DisplayName("the cash account is hidden from the bank account listing")
	void cashWalletExcludedFromListing() {
		this.accountService.listBankAccounts("user-1");
		// The repository method used is the one that filters it out.
		org.mockito.Mockito.verify(this.accountRepository).findByUserIdAndSystemCashWalletFalse("user-1");
	}

}
