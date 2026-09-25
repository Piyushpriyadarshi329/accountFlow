package com.accountflow.transaction.service;

import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.exception.TransactionNotFoundException;
import com.accountflow.common.idempotency.IdempotencyRecord;
import com.accountflow.common.idempotency.IdempotencyService;
import com.accountflow.common.idempotency.MoneyOperationExecutor;
import com.accountflow.common.mongo.TransientTransactionRetry;
import com.accountflow.ledger.LedgerService;
import com.accountflow.ledger.PostingCommand;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transaction.dto.PostTransactionRequest;
import com.accountflow.transaction.dto.TransactionResponse;
import com.accountflow.transaction.mapper.TransactionMapper;
import com.accountflow.transaction.dto.TransactionFilter;
import com.accountflow.transaction.export.TransactionCsvWriter;
import com.accountflow.transaction.repository.TransactionRepository;
import com.accountflow.transaction.repository.TransactionSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

	private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

	/** A PDF is a document to read; past this the CSV export is the right tool. */
	private static final int MAX_PDF_ROWS = 2000;

	private final LedgerService ledgerService;

	private final TransactionRepository transactionRepository;

	private final IdempotencyService idempotencyService;

	private final MoneyOperationExecutor executor;

	private final TransactionSearchRepository searchRepository;

	private final com.accountflow.user.repository.UserRepository userRepository;

	public TransactionService(LedgerService ledgerService, TransactionRepository transactionRepository,
			IdempotencyService idempotencyService, MoneyOperationExecutor executor,
			TransactionSearchRepository searchRepository,
			com.accountflow.user.repository.UserRepository userRepository) {
		this.ledgerService = ledgerService;
		this.transactionRepository = transactionRepository;
		this.idempotencyService = idempotencyService;
		this.executor = executor;
		this.searchRepository = searchRepository;
		this.userRepository = userRepository;
	}

	public TransactionResponse post(String userId, String accountId, TransactionType type,
			PostTransactionRequest request, String idempotencyKey) {
		requireIdempotencyKey(idempotencyKey);

		String endpoint = type.name().toLowerCase() + ":" + accountId;
		String fingerprint = IdempotencyService.fingerprint(accountId, type, request.amount(), request.category(),
				request.description(), request.merchant());

		PostingCommand command = PostingCommand.builder()
			.userId(userId)
			.accountId(accountId)
			.type(type)
			.amount(request.amount())
			.category(request.category())
			.description(request.description())
			.merchant(request.merchant())
			.transactionDate(request.transactionDate())
			.metadata(request.metadata())
			.idempotencyKey(idempotencyKey)
			.build();

		try {
			// Concurrent postings to the same account make MongoDB abort one
			// transaction with a transient WriteConflict. Retrying restarts the
			// whole transaction, so the second attempt sees the committed balance
			// and produces the real outcome instead of a 500.
			Transaction posted = TransientTransactionRetry.execute(() -> this.executor.execute(userId, endpoint,
					idempotencyKey, fingerprint, () -> this.ledgerService.post(command), Transaction::getId));
			return TransactionMapper.toResponse(posted);
		}
		catch (DuplicateKeyException ex) {
			// The same key already claimed this operation: replay the original
			// result rather than moving money a second time.
			log.info("Idempotent replay for key {} on {}", idempotencyKey, endpoint);
			return replay(userId, endpoint, idempotencyKey, fingerprint);
		}
		catch (RuntimeException ex) {
			if (TransientTransactionRetry.isRetryable(ex) || ex instanceof TransientDataAccessException) {
				// Contention outlasted the retry budget. This is retryable by the
				// caller, so say so rather than reporting a server fault.
				log.warn("Giving up on {} after repeated transaction conflicts", endpoint);
				throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS,
						"This account is busy with another request; please retry");
			}
			throw ex;
		}
	}

	public TransactionResponse get(String userId, String transactionId) {
		return this.transactionRepository.findByIdAndUserId(transactionId, userId)
			.map(TransactionMapper::toResponse)
			.orElseThrow(TransactionNotFoundException::new);
	}

	public Page<TransactionResponse> listForAccount(String userId, String accountId, Pageable pageable) {
		return this.transactionRepository.findByUserIdAndAccountId(userId, accountId, pageable)
			.map(TransactionMapper::toResponse);
	}

	public Page<TransactionResponse> list(String userId, Pageable pageable) {
		return this.transactionRepository.findByUserId(userId, pageable).map(TransactionMapper::toResponse);
	}

	/** The signed-in user's own ledger, filtered. Scoped by userId at the query. */
	public Page<TransactionResponse> search(String userId, TransactionFilter filter, Pageable pageable) {
		return this.searchRepository.search(userId, filter, pageable).map(TransactionMapper::toResponse);
	}

	/**
	 * The caller's own ledger as a PDF.
	 *
	 * <p>Unlike the CSV, this cannot stream: a paginated document has to know
	 * its rows. It is therefore capped, and says so on the page when it bites -
	 * the CSV export stays the route for a whole year of data.
	 */
	public void exportPdf(String userId, TransactionFilter filter, String holderName, String holderEmail,
			java.io.OutputStream out) throws java.io.IOException {
		// Fall back to a lookup so the document names the person, not just their
		// address - the caller often only has the email from the token.
		String name = (holderName != null && !holderName.isBlank()) ? holderName
				: this.userRepository.findById(userId)
					.map(com.accountflow.user.domain.User::fullName)
					.orElse(null);
		java.util.List<com.accountflow.transaction.domain.Transaction> rows;
		try (java.util.stream.Stream<com.accountflow.transaction.domain.Transaction> stream = this.searchRepository
			.stream(userId, filter)) {
			rows = stream.limit(MAX_PDF_ROWS + 1L).collect(java.util.stream.Collectors.toList());
		}
		boolean truncated = rows.size() > MAX_PDF_ROWS;
		if (truncated) {
			rows = rows.subList(0, MAX_PDF_ROWS);
		}
		// The cursor gives newest first; a document reads better oldest first.
		java.util.Collections.reverse(rows);
		com.accountflow.transaction.export.TransactionPdfWriter.write(rows, name, holderEmail, filter.from(),
				filter.to(), truncated, out);
	}

	/** Streams the caller's own ledger as CSV. */
	public long export(String userId, TransactionFilter filter, java.io.Writer out) throws java.io.IOException {
		try (java.util.stream.Stream<com.accountflow.transaction.domain.Transaction> rows = this.searchRepository
			.stream(userId, filter)) {
			return TransactionCsvWriter.write(rows, out);
		}
	}

	private TransactionResponse replay(String userId, String endpoint, String idempotencyKey, String fingerprint) {
		IdempotencyRecord existing = this.idempotencyService.resolveExisting(userId, endpoint, idempotencyKey,
				fingerprint);
		return this.transactionRepository.findByIdAndUserId(existing.getResourceId(), userId)
			.map(TransactionMapper::toResponse)
			.orElseThrow(TransactionNotFoundException::new);
	}

	private static void requireIdempotencyKey(String idempotencyKey) {
		// Optional idempotency is idempotency that is missing exactly when the
		// network fails, so the header is mandatory rather than defaulted.
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
					"An Idempotency-Key header is required for money-moving requests");
		}
	}

}
