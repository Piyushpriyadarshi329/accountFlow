package com.accountflow.admin.service;

import java.io.Writer;
import java.util.Locale;
import java.util.stream.Stream;

import com.accountflow.account.repository.AccountRepository;
import com.accountflow.admin.dto.AdminUserResponse;
import com.accountflow.admin.dto.CreateUserRequest;
import com.accountflow.admin.dto.UpdateUserRequest;
import com.accountflow.auth.service.RefreshTokenService;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.DuplicateEmailException;
import com.accountflow.common.exception.DuplicatePhoneException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.exception.UserNotFoundException;
import com.accountflow.transaction.domain.Transaction;
import com.accountflow.transaction.dto.TransactionFilter;
import com.accountflow.transaction.dto.TransactionResponse;
import com.accountflow.transaction.export.TransactionCsvWriter;
import com.accountflow.transaction.mapper.TransactionMapper;
import com.accountflow.transaction.repository.TransactionRepository;
import com.accountflow.transaction.repository.TransactionSearchRepository;
import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import com.accountflow.user.repository.UserRepository;
import com.accountflow.user.repository.UserSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administrative management of users and their ledgers.
 *
 * <p>Two safeguards run through everything here. An administrator cannot act on
 * their own account - no self-demotion, no self-deletion - because the mistake
 * is unrecoverable from inside the application. And the last active
 * administrator cannot be removed or demoted, which is the same mistake with an
 * extra step.
 */
@Service
public class AdminUserService {

	private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

	private final UserRepository userRepository;

	private final UserSearchRepository userSearchRepository;

	private final AccountRepository accountRepository;

	private final TransactionRepository transactionRepository;

	private final TransactionSearchRepository transactionSearchRepository;

	private final RefreshTokenService refreshTokenService;

	private final PasswordEncoder passwordEncoder;

	private final com.accountflow.statement.StatementService statementService;

	private final com.accountflow.transaction.service.TransactionService transactionService;

	public AdminUserService(UserRepository userRepository, UserSearchRepository userSearchRepository,
			AccountRepository accountRepository, TransactionRepository transactionRepository,
			TransactionSearchRepository transactionSearchRepository, RefreshTokenService refreshTokenService,
			PasswordEncoder passwordEncoder, com.accountflow.statement.StatementService statementService,
			com.accountflow.transaction.service.TransactionService transactionService) {
		this.userRepository = userRepository;
		this.userSearchRepository = userSearchRepository;
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.transactionSearchRepository = transactionSearchRepository;
		this.refreshTokenService = refreshTokenService;
		this.passwordEncoder = passwordEncoder;
		this.statementService = statementService;
		this.transactionService = transactionService;
	}

	/* ------------------------------------------------------------- read ---- */

	public Page<AdminUserResponse> list(String query, UserStatus status, Role role, Pageable pageable) {
		return this.userSearchRepository.search(query, status, role, pageable).map(this::toResponse);
	}

	public AdminUserResponse get(String userId) {
		return toResponse(require(userId));
	}

	public Page<TransactionResponse> transactionsOf(String userId, TransactionFilter filter, Pageable pageable) {
		require(userId);
		return this.transactionSearchRepository.search(userId, filter, pageable).map(TransactionMapper::toResponse);
	}

	/** Streams the ledger straight to the response; nothing is buffered. */
	public long exportTransactions(String userId, TransactionFilter filter, Writer out) throws java.io.IOException {
		require(userId);
		try (Stream<Transaction> transactions = this.transactionSearchRepository.stream(userId, filter)) {
			return TransactionCsvWriter.write(transactions, out);
		}
	}

	/** A user's ledger as a PDF, for an administrator. */
	public void exportTransactionsPdf(String userId, TransactionFilter filter, java.io.OutputStream out)
			throws java.io.IOException {
		com.accountflow.user.domain.User holder = require(userId);
		this.transactionService.exportPdf(userId, filter, holder.fullName(), holder.getEmail(), out);
	}

	/** The user's accounts, so an administrator can pick one to take a statement for. */
	public java.util.List<com.accountflow.account.dto.AccountResponse> accountsOf(String userId) {
		require(userId);
		return this.accountRepository.findByUserId(userId)
			.stream()
			.map(com.accountflow.account.mapper.AccountMapper::toResponse)
			.toList();
	}

	/** An account statement for any user, with the same opening/closing figures. */
	public com.accountflow.statement.Statement statementFor(String userId, String accountId,
			java.time.LocalDate from, java.time.LocalDate to) {
		require(userId);
		return this.statementService.build(userId, accountId, from, to);
	}

	/* ------------------------------------------------------------ write ---- */

	public AdminUserResponse create(CreateUserRequest request) {
		String email = normalizeEmail(request.email());
		String phone = blankToNull(request.phone());

		if (this.userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}
		if (phone != null && this.userRepository.existsByPhone(phone)) {
			throw new DuplicatePhoneException();
		}

		User user = User.builder()
			.firstName(request.firstName().trim())
			.lastName(blankToNull(request.lastName()))
			.email(email)
			.phone(phone)
			.passwordHash(this.passwordEncoder.encode(request.password()))
			.role((request.role() != null) ? request.role() : Role.USER)
			.status((request.status() != null) ? request.status() : UserStatus.ACTIVE)
			.build();

		try {
			user = this.userRepository.save(user);
		}
		catch (DuplicateKeyException ex) {
			throw (ex.getMessage() != null && ex.getMessage().contains("phone")) ? new DuplicatePhoneException()
					: new DuplicateEmailException();
		}
		log.info("Admin created user {} with role {}", user.getId(), user.getRole());
		return toResponse(user);
	}

	public AdminUserResponse update(String userId, UpdateUserRequest request, String actingAdminId) {
		User user = require(userId);
		Role newRole = (request.role() != null) ? request.role() : user.getRole();
		UserStatus newStatus = (request.status() != null) ? request.status() : user.getStatus();

		if (user.getId().equals(actingAdminId)
				&& (newRole != user.getRole() || newStatus != user.getStatus())) {
			throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF,
					"You cannot change your own role or status");
		}
		if (losesAdminRights(user, newRole, newStatus)) {
			requireAnotherActiveAdmin();
		}

		String phone = blankToNull(request.phone());
		if (phone != null && !phone.equals(user.getPhone()) && this.userRepository.existsByPhone(phone)) {
			throw new DuplicatePhoneException();
		}

		boolean losingAccess = user.getStatus() == UserStatus.ACTIVE && newStatus != UserStatus.ACTIVE;

		user.setFirstName(request.firstName().trim());
		user.setLastName(blankToNull(request.lastName()));
		user.setPhone(phone);
		user.setRole(newRole);
		user.setStatus(newStatus);
		if (newStatus == UserStatus.ACTIVE) {
			// Reactivating clears a lockout; otherwise the user stays locked out
			// by a counter nobody can see.
			user.setFailedLoginAttempts(0);
			user.setLockedUntil(null);
		}
		User saved = this.userRepository.save(user);

		if (losingAccess) {
			revokeAccess(saved);
		}
		log.info("Admin updated user {} (role {}, status {})", userId, newRole, newStatus);
		return toResponse(saved);
	}

	public void resetPassword(String userId, String rawPassword) {
		User user = require(userId);
		user.setPasswordHash(this.passwordEncoder.encode(rawPassword));
		user.setFailedLoginAttempts(0);
		user.setLockedUntil(null);
		revokeAccess(this.userRepository.save(user));
		log.info("Admin reset the password for user {}", userId);
	}

	/**
	 * Soft delete: the user loses access immediately, but their accounts and
	 * ledger survive. Financial history is the record of what happened, and an
	 * administrator removing a person should not erase it.
	 */
	public AdminUserResponse deactivate(String userId, String actingAdminId) {
		User user = require(userId);
		refuseSelf(userId, actingAdminId, "deactivate your own account");
		if (user.getRole() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE) {
			requireAnotherActiveAdmin();
		}

		user.setStatus(UserStatus.INACTIVE);
		User saved = this.userRepository.save(user);
		revokeAccess(saved);
		log.info("Admin deactivated user {}", userId);
		return toResponse(saved);
	}

	/**
	 * Hard delete, for a user created by mistake. Refused once they hold
	 * anything financial - deleting the owner would orphan accounts and
	 * transactions, leaving a ledger that no longer reconciles to anybody.
	 */
	public void delete(String userId, String actingAdminId) {
		User user = require(userId);
		refuseSelf(userId, actingAdminId, "delete your own account");
		if (user.getRole() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE) {
			requireAnotherActiveAdmin();
		}

		long accounts = this.accountRepository.countByUserId(userId);
		long transactions = this.transactionRepository.countByUserId(userId);
		if (accounts > 0 || transactions > 0) {
			throw new BusinessException(ErrorCode.USER_HAS_RECORDS,
					("This user holds %d account(s) and %d transaction(s), so deleting them would orphan "
							+ "financial records. Deactivate them instead.").formatted(accounts, transactions));
		}

		this.refreshTokenService.revokeAllForUser(userId);
		this.userRepository.delete(user);
		log.info("Admin permanently deleted user {} (no financial records)", userId);
	}

	/* ----------------------------------------------------------- helpers ---- */

	private void revokeAccess(User user) {
		this.refreshTokenService.revokeAllForUser(user.getId());
		// Bumping the version invalidates access tokens already issued, so the
		// change takes effect now rather than in up to fifteen minutes.
		user.setTokenVersion(user.getTokenVersion() + 1);
		this.userRepository.save(user);
	}

	private static boolean losesAdminRights(User user, Role newRole, UserStatus newStatus) {
		return user.getRole() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE
				&& (newRole != Role.ADMIN || newStatus != UserStatus.ACTIVE);
	}

	private void requireAnotherActiveAdmin() {
		if (this.userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
			throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF,
					"This is the only active administrator; promote another one first");
		}
	}

	private static void refuseSelf(String userId, String actingAdminId, String action) {
		if (userId.equals(actingAdminId)) {
			throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF, "You cannot " + action);
		}
	}

	private User require(String userId) {
		return this.userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
	}

	private AdminUserResponse toResponse(User user) {
		return new AdminUserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
				user.getPhone(), user.getRole(), user.getStatus(),
				this.accountRepository.countByUserIdAndSystemCashWalletFalse(user.getId()),
				this.transactionRepository.countByUserId(user.getId()), user.getFailedLoginAttempts(),
				user.getLockedUntil(), user.getCreatedAt(), user.getUpdatedAt());
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String value) {
		return (value != null && !value.isBlank()) ? value.trim() : null;
	}

}
