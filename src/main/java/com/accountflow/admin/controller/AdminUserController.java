package com.accountflow.admin.controller;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import com.accountflow.admin.dto.AdminUserResponse;
import com.accountflow.admin.dto.CreateUserRequest;
import com.accountflow.admin.dto.ResetPasswordRequest;
import com.accountflow.admin.dto.UpdateUserRequest;
import com.accountflow.admin.service.AdminUserService;
import com.accountflow.common.api.ApiResponse;
import com.accountflow.common.api.PaginationMeta;
import com.accountflow.security.AuthenticatedUser;
import com.accountflow.transaction.domain.TransactionType;
import com.accountflow.transaction.dto.TransactionFilter;
import com.accountflow.transaction.dto.TransactionResponse;
import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration. Every method is behind {@code ROLE_ADMIN}, enforced here as
 * well as by the URL rule in SecurityConfig - two independent checks, because
 * one forgotten path pattern should not expose user management.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "User management and ledger access. Administrators only.")
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping("/users")
	@Operation(summary = "List and search users",
			description = "Matches the query against email, name and phone.")
	public ApiResponse<List<AdminUserResponse>> list(@RequestParam(required = false) String query,
			@RequestParam(required = false) UserStatus status, @RequestParam(required = false) Role role,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		Page<AdminUserResponse> page = this.adminUserService.list(query, status, role, pageable);
		return ApiResponse.paged(page.getContent(), PaginationMeta.from(page));
	}

	@GetMapping("/users/{userId}")
	@Operation(summary = "Get one user")
	public ApiResponse<AdminUserResponse> get(@PathVariable String userId) {
		return ApiResponse.of(this.adminUserService.get(userId));
	}

	@PostMapping("/users")
	@Operation(summary = "Create a user")
	public ResponseEntity<ApiResponse<AdminUserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.of(this.adminUserService.create(request), "User created"));
	}

	@PutMapping("/users/{userId}")
	@Operation(summary = "Update a user's details, role or status",
			description = "Moving a user out of ACTIVE revokes their tokens immediately. You cannot change "
					+ "your own role or status, and the last active administrator cannot be demoted.")
	public ApiResponse<AdminUserResponse> update(@PathVariable String userId,
			@Valid @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		return ApiResponse.of(this.adminUserService.update(userId, request, currentUser.userId()), "User updated");
	}

	@PostMapping("/users/{userId}/password")
	@Operation(summary = "Set a new password for a user",
			description = "Also clears any lockout and signs the user out everywhere.")
	public ApiResponse<Void> resetPassword(@PathVariable String userId,
			@Valid @RequestBody ResetPasswordRequest request) {
		this.adminUserService.resetPassword(userId, request.password());
		return ApiResponse.of(null, "Password reset");
	}

	@PostMapping("/users/{userId}/deactivate")
	@Operation(summary = "Deactivate a user",
			description = "The reversible removal: access is revoked immediately, accounts and ledger are kept.")
	public ApiResponse<AdminUserResponse> deactivate(@PathVariable String userId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		return ApiResponse.of(this.adminUserService.deactivate(userId, currentUser.userId()), "User deactivated");
	}

	@DeleteMapping("/users/{userId}")
	@Operation(summary = "Permanently delete a user",
			description = "Only for a user who holds no accounts and no transactions. Anyone with financial "
					+ "records is refused with USER_HAS_RECORDS - deactivate them instead, so the ledger "
					+ "still reconciles.")
	public ApiResponse<Void> delete(@PathVariable String userId,
			@AuthenticationPrincipal AuthenticatedUser currentUser) {
		this.adminUserService.delete(userId, currentUser.userId());
		return ApiResponse.of(null, "User deleted");
	}

	/* ------------------------------------------------------------ ledger ---- */

	@GetMapping("/users/{userId}/transactions")
	@Operation(summary = "A user's ledger, filtered",
			description = "from/to are calendar dates in UTC and both are inclusive.")
	public ApiResponse<List<TransactionResponse>> transactions(@PathVariable String userId,
			@RequestParam(required = false) String accountId,
			@RequestParam(required = false) TransactionType transactionType,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) BigDecimal minAmount,
			@RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) String merchant, @RequestParam(required = false) String query,
			@PageableDefault(size = 20, sort = "transactionDate",
					direction = Sort.Direction.DESC) Pageable pageable) {
		TransactionFilter filter = new TransactionFilter(accountId, transactionType, category, from, to, minAmount,
				maxAmount, merchant, query);
		Page<TransactionResponse> page = this.adminUserService.transactionsOf(userId, filter, pageable);
		return ApiResponse.paged(page.getContent(), PaginationMeta.from(page));
	}

	@GetMapping("/users/{userId}/accounts")
	@Operation(summary = "List a user's accounts",
			description = "Includes the cash account, so a statement can be taken for it too.")
	public ApiResponse<List<com.accountflow.account.dto.AccountResponse>> accounts(@PathVariable String userId) {
		return ApiResponse.of(this.adminUserService.accountsOf(userId));
	}

	@GetMapping("/users/{userId}/accounts/{accountId}/statement")
	@Operation(summary = "Download a statement for one of a user's accounts",
			description = "format=csv (default) or format=pdf. Includes opening and closing balances.")
	public void statement(@PathVariable String userId, @PathVariable String accountId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "csv") String format, HttpServletResponse response) throws IOException {
		com.accountflow.account.controller.AccountController
			.writeStatement(this.adminUserService.statementFor(userId, accountId, from, to), format, response);
	}

	@GetMapping("/users/{userId}/transactions/export")
	@Operation(summary = "Download a user's ledger",
			description = "format=csv (default) or format=pdf. CSV is streamed so any range is safe; PDF is "
					+ "a paginated document capped at 2000 rows.")
	public void export(@PathVariable String userId, @RequestParam(required = false) String accountId,
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
		String email = this.adminUserService.get(userId).email();
		String stem = com.accountflow.common.util.Filenames.build(email, null, "transactions", from, to);

		if ("pdf".equalsIgnoreCase(format)) {
			response.setContentType("application/pdf");
			response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"%s.pdf\"".formatted(stem));
			try (java.io.OutputStream out = response.getOutputStream()) {
				this.adminUserService.exportTransactionsPdf(userId, filter, out);
			}
			return;
		}

		response.setContentType("text/csv");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s.csv\"".formatted(stem));

		try (Writer writer = response.getWriter()) {
			this.adminUserService.exportTransactions(userId, filter, writer);
		}
	}

}
