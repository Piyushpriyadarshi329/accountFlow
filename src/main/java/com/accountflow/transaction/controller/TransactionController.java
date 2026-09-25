package com.accountflow.transaction.controller;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import com.accountflow.common.api.ApiResponse;
import com.accountflow.common.api.PaginationMeta;
import com.accountflow.security.AuthenticatedUser;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transaction.dto.PostTransactionRequest;
import com.accountflow.transaction.dto.TransactionFilter;
import com.accountflow.transaction.dto.TransactionResponse;
import com.accountflow.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transactions", description = "Credits, debits and transaction history")
public class TransactionController {

	static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final TransactionService transactionService;

	public TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@PostMapping("/accounts/{accountId}/transactions/credit")
	@Operation(summary = "Credit money into an account",
			description = "Requires an Idempotency-Key header. Retrying with the same key returns the original transaction instead of posting again.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Posted")
	public ResponseEntity<ApiResponse<TransactionResponse>> credit(
			@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable String accountId,
			@Valid @RequestBody PostTransactionRequest request,
			@Parameter(description = "Unique key identifying this operation",
					required = true) @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
		TransactionResponse posted = this.transactionService.post(currentUser.userId(), accountId,
				TransactionType.CREDIT, request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(posted, "Credit posted"));
	}

	@PostMapping("/accounts/{accountId}/transactions/debit")
	@Operation(summary = "Debit money from an account",
			description = "Rejected with INSUFFICIENT_BALANCE unless the account has the funds, or a credit limit covering the shortfall.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Posted")
	public ResponseEntity<ApiResponse<TransactionResponse>> debit(
			@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable String accountId,
			@Valid @RequestBody PostTransactionRequest request,
			@Parameter(description = "Unique key identifying this operation",
					required = true) @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
		TransactionResponse posted = this.transactionService.post(currentUser.userId(), accountId,
				TransactionType.DEBIT, request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(posted, "Debit posted"));
	}

	@GetMapping("/accounts/{accountId}/transactions")
	@Operation(summary = "List one account's transactions, newest first")
	public ApiResponse<List<TransactionResponse>> listForAccount(
			@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable String accountId,
			@PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
		Page<TransactionResponse> page = this.transactionService.listForAccount(currentUser.userId(), accountId,
				pageable);
		return ApiResponse.paged(page.getContent(), PaginationMeta.from(page));
	}

	@GetMapping("/transactions")
	@Operation(summary = "List the signed-in user's transactions, filtered",
			description = "Every filter is optional. `from` and `to` are calendar dates in UTC and both are "
					+ "inclusive, so from=2026-09-01&to=2026-09-30 covers September whole.")
	public ApiResponse<List<TransactionResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@RequestParam(required = false) String accountId,
			@RequestParam(required = false) TransactionType transactionType,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) BigDecimal minAmount,
			@RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) String merchant, @RequestParam(required = false) String query,
			@PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
		TransactionFilter filter = new TransactionFilter(accountId, transactionType, category, from, to, minAmount,
				maxAmount, merchant, query);
		Page<TransactionResponse> page = filter.isEmpty()
				? this.transactionService.list(currentUser.userId(), pageable)
				: this.transactionService.search(currentUser.userId(), filter, pageable);
		return ApiResponse.paged(page.getContent(), PaginationMeta.from(page));
	}

	@GetMapping("/transactions/export")
	@Operation(summary = "Download the signed-in user's transactions",
			description = "format=csv (default) or format=pdf. Same filters as the list endpoint. CSV is "
					+ "streamed from a cursor so any date range is safe; PDF is a paginated document and is "
					+ "capped at 2000 rows.")
	public void export(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@RequestParam(required = false) String accountId,
			@RequestParam(required = false) TransactionType transactionType,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) BigDecimal minAmount,
			@RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) String merchant, @RequestParam(required = false) String query,
			@RequestParam(defaultValue = "csv") String format, HttpServletResponse response) throws IOException {
		TransactionFilter filter = new TransactionFilter(accountId, transactionType, category, from, to, minAmount,
				maxAmount, merchant, query);
		String stem = com.accountflow.common.util.Filenames.build(currentUser.email(), null, "transactions", from,
				to);

		if ("pdf".equalsIgnoreCase(format)) {
			response.setContentType("application/pdf");
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"%s.pdf\"".formatted(stem));
			try (java.io.OutputStream out = response.getOutputStream()) {
				this.transactionService.exportPdf(currentUser.userId(), filter, null, currentUser.email(), out);
			}
			return;
		}

		response.setContentType("text/csv");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		// Name carries who and when, so a folder of exports stays readable.
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s.csv\"".formatted(stem));

		try (Writer writer = response.getWriter()) {
			this.transactionService.export(currentUser.userId(), filter, writer);
		}
	}

	@GetMapping("/transactions/{transactionId}")
	@Operation(summary = "Get one transaction")
	public ApiResponse<TransactionResponse> get(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable String transactionId) {
		return ApiResponse.of(this.transactionService.get(currentUser.userId(), transactionId));
	}

}
