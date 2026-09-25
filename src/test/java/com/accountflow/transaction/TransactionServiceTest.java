package com.accountflow.transaction;

import java.math.BigDecimal;
import java.util.Optional;

import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.idempotency.IdempotencyRecord;
import com.accountflow.common.idempotency.IdempotencyService;
import com.accountflow.common.idempotency.IdempotencyStatus;
import com.accountflow.common.idempotency.MoneyOperationExecutor;
import com.accountflow.ledger.LedgerService;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transaction.dto.PostTransactionRequest;
import com.accountflow.transaction.repository.TransactionRepository;
import com.accountflow.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

	@Mock
	private LedgerService ledgerService;

	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private IdempotencyService idempotencyService;

	@Mock
	private MoneyOperationExecutor executor;

	@Mock
	private com.accountflow.transaction.repository.TransactionSearchRepository searchRepository;

	@Mock
	private com.accountflow.user.repository.UserRepository userRepository;

	private TransactionService service() {
		return new TransactionService(this.ledgerService, this.transactionRepository, this.idempotencyService,
				this.executor, this.searchRepository, this.userRepository);
	}

	private static PostTransactionRequest request() {
		return new PostTransactionRequest(new BigDecimal("500.00"), "FOOD", "Lunch", "Cafe", null, null);
	}

	@Test
	@DisplayName("a money-moving request without an Idempotency-Key is refused")
	void requiresIdempotencyKey() {
		for (String key : new String[] { null, "", "   " }) {
			assertThatThrownBy(
					() -> service().post("user-1", "acct-1", TransactionType.DEBIT, request(), key))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		}
	}

	@Test
	@DisplayName("a retry with the same key replays the original transaction instead of posting again")
	void replaysOnDuplicateKey() {
		Transaction original = Transaction.builder()
			.id("txn-1")
			.transactionReference("TXN-ORIGINAL")
			.amount(new BigDecimal("500.00"))
			.build();

		given(this.executor.execute(anyString(), anyString(), anyString(), anyString(), any(), any()))
			.willThrow(new DuplicateKeyException("duplicate"));
		given(this.idempotencyService.resolveExisting(anyString(), anyString(), anyString(), anyString()))
			.willReturn(IdempotencyRecord.builder()
				.id("idem-1")
				.resourceId("txn-1")
				.status(IdempotencyStatus.COMPLETED)
				.build());
		given(this.transactionRepository.findByIdAndUserId("txn-1", "user-1")).willReturn(Optional.of(original));

		var response = service().post("user-1", "acct-1", TransactionType.DEBIT, request(), "KEY-1");

		assertThat(response.transactionReference()).isEqualTo("TXN-ORIGINAL");
	}

	@Test
	@DisplayName("the happy path delegates the posting to the ledger")
	void delegatesToLedger() {
		Transaction posted = Transaction.builder()
			.id("txn-1")
			.transactionReference("TXN-NEW")
			.amount(new BigDecimal("500.00"))
			.build();
		given(this.executor.execute(anyString(), anyString(), anyString(), anyString(), any(), any()))
			.willReturn(posted);

		var response = service().post("user-1", "acct-1", TransactionType.CREDIT, request(), "KEY-2");

		assertThat(response.transactionReference()).isEqualTo("TXN-NEW");
	}

	@Test
	@DisplayName("a concurrent retry still holding the claim reports REQUEST_IN_PROGRESS")
	void reportsInFlightRetry() {
		given(this.executor.execute(anyString(), anyString(), anyString(), anyString(), any(), any()))
			.willThrow(new org.springframework.dao.TransientDataAccessResourceException("write conflict"));

		assertThatThrownBy(() -> service().post("user-1", "acct-1", TransactionType.DEBIT, request(), "KEY-3"))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.REQUEST_IN_PROGRESS);
	}

}
