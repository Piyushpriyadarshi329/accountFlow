package com.accountflow.transfer.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.accountflow.common.api.ApiResponse;
import com.accountflow.common.api.PaginationMeta;
import com.accountflow.security.AuthenticatedUser;
import com.accountflow.transfer.dto.BankCashTransferRequest;
import com.accountflow.transfer.dto.TransferRequest;
import com.accountflow.transfer.dto.TransferResponse;
import com.accountflow.transfer.service.TransferService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Moving money between accounts, and between bank and cash")
public class TransferController {

	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final TransferService transferService;

	public TransferController(TransferService transferService) {
		this.transferService = transferService;
	}

	@PostMapping
	@Operation(summary = "Transfer between two of the signed-in user's accounts",
			description = "Both legs commit together inside one MongoDB transaction, or neither does.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Posted")
	public ResponseEntity<ApiResponse<TransferResponse>> transfer(
			@AuthenticationPrincipal AuthenticatedUser currentUser, @Valid @RequestBody TransferRequest request,
			@io.swagger.v3.oas.annotations.Parameter(description = "Unique key identifying this operation; retrying with the same key returns the original result", required = true) @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
		TransferResponse created = this.transferService.transfer(currentUser.userId(), request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created, "Transfer completed"));
	}

	@PostMapping("/bank-to-cash")
	@Operation(summary = "Withdraw cash from a bank account",
			description = "Debits the bank account and credits the cash wallet.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Posted")
	public ResponseEntity<ApiResponse<TransferResponse>> bankToCash(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody BankCashTransferRequest request,
			@io.swagger.v3.oas.annotations.Parameter(description = "Unique key identifying this operation; retrying with the same key returns the original result", required = true) @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
		TransferResponse created = this.transferService.bankToCash(currentUser.userId(), request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created, "Cash withdrawn"));
	}

	@PostMapping("/cash-to-bank")
	@Operation(summary = "Deposit cash into a bank account",
			description = "Debits the cash wallet and credits the bank account.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Posted")
	public ResponseEntity<ApiResponse<TransferResponse>> cashToBank(
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody BankCashTransferRequest request,
			@io.swagger.v3.oas.annotations.Parameter(description = "Unique key identifying this operation; retrying with the same key returns the original result", required = true) @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
		TransferResponse created = this.transferService.cashToBank(currentUser.userId(), request, idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created, "Cash deposited"));
	}

	@GetMapping("/{transferId}")
	@Operation(summary = "Get one transfer")
	public ApiResponse<TransferResponse> get(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable String transferId) {
		return ApiResponse.of(this.transferService.get(currentUser.userId(), transferId));
	}

	@GetMapping
	@Operation(summary = "List the signed-in user's transfers")
	public ApiResponse<List<TransferResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		Page<TransferResponse> page = this.transferService.list(currentUser.userId(), pageable);
		return ApiResponse.paged(page.getContent(), PaginationMeta.from(page));
	}

}
