package com.accountflow.account.service;

import java.math.BigDecimal;
import java.util.List;

import com.accountflow.account.domain.Account;
import com.accountflow.account.domain.AccountStatus;
import com.accountflow.account.domain.AccountType;
import com.accountflow.account.dto.AccountResponse;
import com.accountflow.account.dto.CreateAccountRequest;
import com.accountflow.account.dto.UpdateAccountRequest;
import com.accountflow.account.mapper.AccountMapper;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.common.exception.AccountNotEmptyException;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.DuplicateAccountNumberException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

	private static final Logger log = LoggerFactory.getLogger(AccountService.class);

	/** Currency for the auto-created cash wallet. */
	private static final String DEFAULT_CURRENCY = "INR";

	private final AccountRepository accountRepository;

	public AccountService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	public AccountResponse create(String userId, CreateAccountRequest request) {
		String currency = request.currency().toUpperCase();
		int scale = Money.scaleOf(currency);

		if (request.accountType() == AccountType.CASH) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"The cash account is created automatically and cannot be added manually");
		}
		BigDecimal creditLimit = (request.creditLimit() != null) ? request.creditLimit() : BigDecimal.ZERO;
		if (creditLimit.signum() > 0 && !request.accountType().supportsCreditLimit()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"A credit limit is only valid on a CREDIT_CARD account");
		}

		String accountNumber = normalize(request.accountNumber());
		if (accountNumber != null && this.accountRepository.existsByUserIdAndAccountNumber(userId, accountNumber)) {
			throw new DuplicateAccountNumberException();
		}

		BigDecimal opening = ((request.openingBalance() != null) ? request.openingBalance() : BigDecimal.ZERO)
			.setScale(scale, Money.ROUNDING);

		Account account = Account.builder()
			.userId(userId)
			.accountName(request.accountName().trim())
			.bankName(normalize(request.bankName()))
			.accountNumber(accountNumber)
			.accountType(request.accountType())
			.currency(currency)
			.openingBalance(opening)
			.currentBalance(opening)
			.creditLimit(creditLimit.setScale(scale, Money.ROUNDING))
			.status(AccountStatus.ACTIVE)
			.systemCashWallet(false)
			.build();

		try {
			account = this.accountRepository.save(account);
		}
		catch (DuplicateKeyException ex) {
			throw new DuplicateAccountNumberException();
		}
		log.info("Created {} account {} for user {}", account.getAccountType(), account.getId(), userId);
		return AccountMapper.toResponse(account);
	}

	public List<AccountResponse> listBankAccounts(String userId) {
		return this.accountRepository.findByUserIdAndSystemCashWalletFalse(userId)
			.stream()
			.map(AccountMapper::toResponse)
			.toList();
	}

	public AccountResponse get(String userId, String accountId) {
		return AccountMapper.toResponse(requireOwned(userId, accountId));
	}

	public AccountResponse update(String userId, String accountId, UpdateAccountRequest request) {
		Account account = requireOwned(userId, accountId);
		if (account.isSystemCashWallet()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The cash account cannot be edited");
		}

		String accountNumber = normalize(request.accountNumber());
		if (accountNumber != null && !accountNumber.equals(account.getAccountNumber())
				&& this.accountRepository.existsByUserIdAndAccountNumber(userId, accountNumber)) {
			throw new DuplicateAccountNumberException();
		}

		account.setAccountName(request.accountName().trim());
		account.setBankName(normalize(request.bankName()));
		account.setAccountNumber(accountNumber);
		// Balance, currency and type are deliberately immutable here: changing
		// them would rewrite the meaning of existing history.
		return AccountMapper.toResponse(this.accountRepository.save(account));
	}

	public AccountResponse updateStatus(String userId, String accountId, AccountStatus status) {
		Account account = requireOwned(userId, accountId);
		if (account.isSystemCashWallet()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The cash account cannot be closed");
		}
		if (status == AccountStatus.CLOSED && account.getCurrentBalance().signum() != 0) {
			throw new AccountNotEmptyException(account.getCurrentBalance());
		}
		account.setStatus(status);
		log.info("Account {} status changed to {}", accountId, status);
		return AccountMapper.toResponse(this.accountRepository.save(account));
	}

	/**
	 * The user's single cash account, created on first use. Modelled as an
	 * ordinary Account so bank and cash share one ledger and one transfer path.
	 */
	public Account getOrCreateCashWallet(String userId) {
		return this.accountRepository.findByUserIdAndAccountType(userId, AccountType.CASH)
			.orElseGet(() -> this.accountRepository.save(Account.builder()
				.userId(userId)
				.accountName("Cash")
				.accountType(AccountType.CASH)
				.currency(DEFAULT_CURRENCY)
				.openingBalance(Money.zero(DEFAULT_CURRENCY))
				.currentBalance(Money.zero(DEFAULT_CURRENCY))
				.creditLimit(Money.zero(DEFAULT_CURRENCY))
				.status(AccountStatus.ACTIVE)
				.systemCashWallet(true)
				.build()));
	}

	private Account requireOwned(String userId, String accountId) {
		return this.accountRepository.findByIdAndUserId(accountId, userId)
			.orElseThrow(AccountNotFoundException::new);
	}

	private static String normalize(String value) {
		return (value != null && !value.isBlank()) ? value.trim() : null;
	}

}
