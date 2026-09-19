package com.accountflow.account.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.accountflow.account.dto.AccountResponse;
import com.accountflow.account.dto.CreateAccountRequest;
import com.accountflow.account.dto.UpdateAccountRequest;
import com.accountflow.account.dto.UpdateAccountStatusRequest;
import com.accountflow.account.service.AccountService;
import com.accountflow.common.api.ApiResponse;
import com.accountflow.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin by design: bind, delegate, wrap. Every method takes the owner from the
 * token, never from the path, and the service resolves the account with an
 * owner-scoped query.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Bank accounts, cards and wallets belonging to the signed-in user")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping
	@Operation(summary = "Create an account")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "You already have an account with this number")
	public ResponseEntity<ApiResponse<AccountResponse>> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody CreateAccountRequest request) {
		AccountResponse created = this.accountService.create(currentUser.userId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created, "Account created"));
	}

	@GetMapping
	@Operation(summary = "List the signed-in user's accounts (excludes the cash account)")
	public ApiResponse<List<AccountResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		return ApiResponse.of(this.accountService.listBankAccounts(currentUser.userId()));
	}

	@GetMapping("/{accountId}")
	@Operation(summary = "Get one account")
	public ApiResponse<AccountResponse> get(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable String accountId) {
		return ApiResponse.of(this.accountService.get(currentUser.userId(), accountId));
	}

	@PutMapping("/{accountId}")
	@Operation(summary = "Update an account's descriptive fields")
	public ApiResponse<AccountResponse> update(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable String accountId, @Valid @RequestBody UpdateAccountRequest request) {
		return ApiResponse.of(this.accountService.update(currentUser.userId(), accountId, request), "Account updated");
	}

	@PatchMapping("/{accountId}/status")
	@Operation(summary = "Activate, deactivate or close an account")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "ACCOUNT_NOT_EMPTY - close requires a zero balance")
	public ApiResponse<AccountResponse> updateStatus(@AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable String accountId, @Valid @RequestBody UpdateAccountStatusRequest request) {
		return ApiResponse.of(this.accountService.updateStatus(currentUser.userId(), accountId, request.status()),
				"Account status updated");
	}

}
