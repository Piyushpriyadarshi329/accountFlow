package com.accountflow.transfer.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.accountflow.account.domain.Account;
import com.accountflow.account.repository.AccountRepository;
import com.accountflow.account.service.AccountService;
import com.accountflow.common.exception.AccountClosedException;
import com.accountflow.common.exception.AccountNotFoundException;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.CurrencyMismatchException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.exception.SameAccountTransferException;
import com.accountflow.common.exception.TransferNotFoundException;
import com.accountflow.common.idempotency.IdempotencyRecord;
import com.accountflow.common.idempotency.IdempotencyService;
import com.accountflow.common.idempotency.MoneyOperationExecutor;
import com.accountflow.common.money.Money;
import com.accountflow.common.mongo.TransientTransactionRetry;
import com.accountflow.common.util.References;
import com.accountflow.ledger.LedgerService;
import com.accountflow.ledger.PostingCommand;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transfer.domain.Transfer;
import com.accountflow.transfer.domain.TransferKind;
import com.accountflow.transfer.domain.TransferStatus;
import com.accountflow.transfer.dto.BankCashTransferRequest;
import com.accountflow.transfer.dto.TransferRequest;
import com.accountflow.transfer.dto.TransferResponse;
import com.accountflow.transfer.mapper.TransferMapper;
import com.accountflow.transfer.repository.TransferRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Moves money between two accounts belonging to the same user.
 *
 * <p>Bank-to-bank, bank-to-cash and cash-to-bank are the same operation: the
 * cash wallet is an ordinary Account, so every direction is two ledger postings
 * plus one transfer record, all inside a single MongoDB transaction. Either the
 * debit, the credit and the record all commit, or none of them do.
 *
 * <h2>Why the legs are posted in account-id order</h2>
 *
 * Two users transferring A-to-B and B-to-A at the same moment would otherwise
 * take write locks on the two documents in opposite orders and conflict
 * needlessly. Always touching them in the same deterministic order removes that
 * class of contention. Which leg lands first is irrelevant to correctness,
 * because the transaction makes the pair atomic.
 */
@Service
public class TransferService {

	private static final Logger log = LoggerFactory.getLogger(TransferService.class);

	private final LedgerService ledgerService;

	private final AccountRepository accountRepository;

	private final AccountService accountService;

	private final TransferRepository transferRepository;

	private final IdempotencyService idempotencyService;

	private final MoneyOperationExecutor executor;

	public TransferService(LedgerService ledgerService, AccountRepository accountRepository,
			AccountService accountService, TransferRepository transferRepository,
			IdempotencyService idempotencyService, MoneyOperationExecutor executor) {
		this.ledgerService = ledgerService;
		this.accountRepository = accountRepository;
		this.accountService = accountService;
		this.transferRepository = transferRepository;
		this.idempotencyService = idempotencyService;
		this.executor = executor;
	}

	public TransferResponse transfer(String userId, TransferRequest request, String idempotencyKey) {
		return run(userId, request.sourceAccountId(), request.destinationAccountId(), request.amount(),
				request.description(), TransferKind.BANK_TO_BANK, idempotencyKey);
	}

	/** Withdrawing cash from a bank account: bank is debited, cash is credited. */
	public TransferResponse bankToCash(String userId, BankCashTransferRequest request, String idempotencyKey) {
		Account cash = this.accountService.getOrCreateCashWallet(userId);
		return run(userId, request.accountId(), cash.getId(), request.amount(), request.description(),
				TransferKind.BANK_TO_CASH, idempotencyKey);
	}

	/** Paying cash into a bank account: cash is debited, bank is credited. */
	public TransferResponse cashToBank(String userId, BankCashTransferRequest request, String idempotencyKey) {
		Account cash = this.accountService.getOrCreateCashWallet(userId);
		return run(userId, cash.getId(), request.accountId(), request.amount(), request.description(),
				TransferKind.CASH_TO_BANK, idempotencyKey);
	}

	public TransferResponse get(String userId, String transferId) {
		return this.transferRepository.findByIdAndUserId(transferId, userId)
			.map(TransferMapper::toResponse)
			.orElseThrow(TransferNotFoundException::new);
	}

	public Page<TransferResponse> list(String userId, Pageable pageable) {
		return this.transferRepository.findByUserId(userId, pageable).map(TransferMapper::toResponse);
	}

	private TransferResponse run(String userId, String sourceAccountId, String destinationAccountId,
			BigDecimal amount, String description, TransferKind kind, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
					"An Idempotency-Key header is required for money-moving requests");
		}
		if (sourceAccountId.equals(destinationAccountId)) {
			throw new SameAccountTransferException();
		}

		String endpoint = "transfer:" + kind;
		String fingerprint = IdempotencyService.fingerprint(sourceAccountId, destinationAccountId, amount,
				description);

		try {
			Transfer completed = TransientTransactionRetry
				.execute(() -> this.executor.execute(userId, endpoint, idempotencyKey, fingerprint,
						() -> apply(userId, sourceAccountId, destinationAccountId, amount, description, kind,
								idempotencyKey),
						Transfer::getId));
			return TransferMapper.toResponse(completed);
		}
		catch (DuplicateKeyException ex) {
			log.info("Idempotent replay for transfer key {}", idempotencyKey);
			IdempotencyRecord existing = this.idempotencyService.resolveExisting(userId, endpoint, idempotencyKey,
					fingerprint);
			return this.transferRepository.findByIdAndUserId(existing.getResourceId(), userId)
				.map(TransferMapper::toResponse)
				.orElseThrow(TransferNotFoundException::new);
		}
		catch (RuntimeException ex) {
			if (TransientTransactionRetry.isRetryable(ex)) {
				throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS,
						"These accounts are busy with another request; please retry");
			}
			throw ex;
		}
	}

	private Transfer apply(String userId, String sourceAccountId, String destinationAccountId, BigDecimal amount,
			String description, TransferKind kind, String idempotencyKey) {
		Account source = requireOwned(userId, sourceAccountId);
		Account destination = requireOwned(userId, destinationAccountId);

		if (!source.getStatus().acceptsPostings()) {
			throw new AccountClosedException(source.getStatus());
		}
		if (!destination.getStatus().acceptsPostings()) {
			throw new AccountClosedException(destination.getStatus());
		}
		if (!source.getCurrency().equals(destination.getCurrency())) {
			// No implicit FX: converting at an unspecified rate is worse than an error.
			throw new CurrencyMismatchException(source.getCurrency(), destination.getCurrency());
		}

		BigDecimal validated = Money.validateAmount(amount, source.getCurrency());
		String transferId = ObjectId.get().toHexString();
		String transferReference = References.transfer();

		PostingCommand debit = PostingCommand.builder()
			.userId(userId)
			.accountId(source.getId())
			.type((kind == TransferKind.CASH_TO_BANK) ? TransactionType.CASH_WITHDRAW : TransactionType.TRANSFER_OUT)
			.direction(com.accountflow.transaction.domain.Direction.OUT)
			.amount(validated)
			.category("TRANSFER")
			.description(description)
			.transfer(transferId, transferReference)
			.build();

		PostingCommand credit = PostingCommand.builder()
			.userId(userId)
			.accountId(destination.getId())
			.type((kind == TransferKind.BANK_TO_CASH) ? TransactionType.CASH_DEPOSIT : TransactionType.TRANSFER_IN)
			.direction(com.accountflow.transaction.domain.Direction.IN)
			.amount(validated)
			.category("TRANSFER")
			.description(description)
			.transfer(transferId, transferReference)
			.build();

		Transaction debitLeg;
		Transaction creditLeg;
		// Deterministic lock order; see the class comment.
		if (source.getId().compareTo(destination.getId()) <= 0) {
			debitLeg = this.ledgerService.post(debit);
			creditLeg = this.ledgerService.post(credit);
		}
		else {
			creditLeg = this.ledgerService.post(credit);
			debitLeg = this.ledgerService.post(debit);
		}

		Instant now = Instant.now();
		// insert, not save: the id is pre-generated, and save() would upsert.
		Transfer transfer = this.transferRepository.insert(Transfer.builder()
			.id(transferId)
			.transferReference(transferReference)
			.userId(userId)
			.sourceAccountId(source.getId())
			.destinationAccountId(destination.getId())
			.sourceAccountName(source.getAccountName())
			.destinationAccountName(destination.getAccountName())
			.amount(validated)
			.currency(source.getCurrency())
			.kind(kind)
			.status(TransferStatus.COMPLETED)
			.debitTransactionId(debitLeg.getId())
			.creditTransactionId(creditLeg.getId())
			.description(description)
			.idempotencyKey(idempotencyKey)
			.createdAt(now)
			.completedAt(now)
			.build());

		log.info("Transfer {} moved {} {} from {} to {}", transferReference, validated, source.getCurrency(),
				source.getId(), destination.getId());
		return transfer;
	}

	private Account requireOwned(String userId, String accountId) {
		return this.accountRepository.findByIdAndUserId(accountId, userId)
			.orElseThrow(AccountNotFoundException::new);
	}

}
