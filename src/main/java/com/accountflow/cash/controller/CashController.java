package com.accountflow.cash.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.accountflow.cash.dto.CashOperationRequest;
import com.accountflow.cash.dto.CashResponse;
import com.accountflow.cash.service.CashService;
import com.accountflow.common.api.ApiResponse;
import com.accountflow.common.api.PaginationMeta;
import com.accountflow.security.AuthenticatedUser;
import com.accountflow.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cash")
@Tag(name = "Cash", description = "Physical cash held by the signed-in user")
public class CashController {

	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final CashService cashService;

	public CashController(CashService cashService) {
		this.cashService = cashService;
	}

	@GetMapping
	@Operation(summary = "Get the current cash balance")
	public ApiResponse<CashResponse> summary(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		return ApiResponse.of(this.cashService.summary(currentUser.userId()));
	}

	@PostMapping("/deposit")
	@Operation(summary = "Record cash received")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Posted")
	public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
			@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody CashOperationRequest request,
			@io.swagger.v3.oas.annotations.Parameter(description = "Unique key identifying this operation; retrying with the same key returns the original result", required = true) @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
		TransactionResponse posted = this.cashService.deposit(currentUser.userId(), request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(posted, "Cash deposit recorded"));
	}

	@PostMapping("/withdraw")
	@Operation(summary = "Record cash spent",
			description = "Refused with INSUFFICIENT_BALANCE when the cash balance will not cover it.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Posted")
	public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
			@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody CashOperationRequest request,
			@io.swagger.v3.oas.annotations.Parameter(description = "Unique key identifying this operation; retrying with the same key returns the original result", required = true) @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
		TransactionResponse posted = this.cashService.withdraw(currentUser.userId(), request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(posted, "Cash withdrawal recorded"));
	}

	@GetMapping("/transactions")
	@Operation(summary = "List cash transactions, newest first")
	public ApiResponse<List<TransactionResponse>> transactions(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
		Page<TransactionResponse> page = this.cashService.transactions(currentUser.userId(), pageable);
		return ApiResponse.paged(page.getContent(), PaginationMeta.from(page));
	}

}
