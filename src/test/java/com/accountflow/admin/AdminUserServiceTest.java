package com.accountflow.admin;

import java.time.Instant;
import java.util.Optional;

import com.accountflow.account.repository.AccountRepository;
import com.accountflow.admin.dto.CreateUserRequest;
import com.accountflow.admin.dto.UpdateUserRequest;
import com.accountflow.admin.service.AdminUserService;
import com.accountflow.auth.service.RefreshTokenService;
import com.accountflow.common.exception.BusinessException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.exception.UserNotFoundException;
import com.accountflow.transaction.repository.TransactionRepository;
import com.accountflow.transaction.repository.TransactionSearchRepository;
import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import com.accountflow.user.repository.UserRepository;
import com.accountflow.user.repository.UserSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

	private static final String ADMIN_ID = "admin-1";

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserSearchRepository userSearchRepository;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private TransactionSearchRepository transactionSearchRepository;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private com.accountflow.statement.StatementService statementService;

	@Mock
	private com.accountflow.transaction.service.TransactionService transactionService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	private AdminUserService service;

	@BeforeEach
	void setUp() {
		this.service = new AdminUserService(this.userRepository, this.userSearchRepository, this.accountRepository,
				this.transactionRepository, this.transactionSearchRepository, this.refreshTokenService,
				this.passwordEncoder, this.statementService, this.transactionService);
		given(this.userRepository.save(any(User.class))).willAnswer((i) -> i.getArgument(0));
		given(this.userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).willReturn(2L);
	}

	private User user(String id, Role role, UserStatus status) {
		User u = User.builder()
			.id(id)
			.firstName("Asha")
			.email(id + "@example.com")
			.passwordHash("$2a$04$hash")
			.role(role)
			.status(status)
			.build();
		given(this.userRepository.findById(id)).willReturn(Optional.of(u));
		return u;
	}

	private static UpdateUserRequest update(Role role, UserStatus status) {
		return new UpdateUserRequest("Asha", "Menon", null, role, status);
	}

	@Test
	@DisplayName("creating a user stores a hash, never the password")
	void createHashesPassword() {
		var response = this.service.create(new CreateUserRequest("Asha", "Menon", "Asha@Example.com ", null,
				"Secret123", null, null));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(this.userRepository).save(captor.capture());
		User saved = captor.getValue();

		assertThat(saved.getPasswordHash()).isNotEqualTo("Secret123").startsWith("$2");
		assertThat(this.passwordEncoder.matches("Secret123", saved.getPasswordHash())).isTrue();
		// Email is normalised the same way self-registration does it.
		assertThat(saved.getEmail()).isEqualTo("asha@example.com");
		assertThat(response.role()).isEqualTo(Role.USER);
		assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("an admin cannot change their own role or status")
	void cannotChangeOwnRoleOrStatus() {
		user(ADMIN_ID, Role.ADMIN, UserStatus.ACTIVE);

		assertThatThrownBy(() -> this.service.update(ADMIN_ID, update(Role.USER, UserStatus.ACTIVE), ADMIN_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.CANNOT_MODIFY_SELF);

		assertThatThrownBy(() -> this.service.update(ADMIN_ID, update(Role.ADMIN, UserStatus.INACTIVE), ADMIN_ID))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("an admin may still edit their own name")
	void canEditOwnName() {
		user(ADMIN_ID, Role.ADMIN, UserStatus.ACTIVE);

		var response = this.service.update(ADMIN_ID, update(Role.ADMIN, UserStatus.ACTIVE), ADMIN_ID);

		assertThat(response.firstName()).isEqualTo("Asha");
	}

	@Test
	@DisplayName("the last active administrator cannot be demoted")
	void cannotDemoteTheLastAdmin() {
		user("admin-2", Role.ADMIN, UserStatus.ACTIVE);
		given(this.userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).willReturn(1L);

		assertThatThrownBy(() -> this.service.update("admin-2", update(Role.USER, UserStatus.ACTIVE), ADMIN_ID))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("only active administrator");
	}

	@Test
	@DisplayName("another administrator can be demoted when one remains")
	void canDemoteWhenAnotherAdminRemains() {
		user("admin-2", Role.ADMIN, UserStatus.ACTIVE);

		assertThat(this.service.update("admin-2", update(Role.USER, UserStatus.ACTIVE), ADMIN_ID).role())
			.isEqualTo(Role.USER);
	}

	@Test
	@DisplayName("deactivating revokes refresh tokens and kills live access tokens")
	void deactivateRevokesAccess() {
		User target = user("user-9", Role.USER, UserStatus.ACTIVE);
		target.setTokenVersion(3);

		var response = this.service.deactivate("user-9", ADMIN_ID);

		assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
		verify(this.refreshTokenService).revokeAllForUser("user-9");
		// Bumping the version is what makes it immediate rather than waiting out
		// the 15-minute access-token lifetime.
		assertThat(target.getTokenVersion()).isEqualTo(4);
	}

	@Test
	@DisplayName("an admin cannot deactivate or delete themselves")
	void cannotRemoveSelf() {
		user(ADMIN_ID, Role.ADMIN, UserStatus.ACTIVE);

		assertThatThrownBy(() -> this.service.deactivate(ADMIN_ID, ADMIN_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.CANNOT_MODIFY_SELF);
		assertThatThrownBy(() -> this.service.delete(ADMIN_ID, ADMIN_ID)).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("a user holding accounts or transactions cannot be hard deleted")
	void refusesToDeleteUserWithFinancialRecords() {
		user("user-9", Role.USER, UserStatus.ACTIVE);
		given(this.accountRepository.countByUserId("user-9")).willReturn(2L);
		given(this.transactionRepository.countByUserId("user-9")).willReturn(37L);

		assertThatThrownBy(() -> this.service.delete("user-9", ADMIN_ID)).isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.USER_HAS_RECORDS);

		verify(this.userRepository, never()).delete(any());
	}

	@Test
	@DisplayName("a user with nothing attached can be deleted outright")
	void deletesUserWithNoRecords() {
		User target = user("user-9", Role.USER, UserStatus.ACTIVE);
		given(this.accountRepository.countByUserId("user-9")).willReturn(0L);
		given(this.transactionRepository.countByUserId("user-9")).willReturn(0L);

		this.service.delete("user-9", ADMIN_ID);

		verify(this.refreshTokenService).revokeAllForUser("user-9");
		verify(this.userRepository).delete(target);
	}

	@Test
	@DisplayName("reactivating a user clears the lockout that would otherwise persist")
	void reactivationClearsLockout() {
		User target = user("user-9", Role.USER, UserStatus.LOCKED);
		target.setFailedLoginAttempts(5);
		target.setLockedUntil(Instant.now().plusSeconds(600));

		this.service.update("user-9", update(Role.USER, UserStatus.ACTIVE), ADMIN_ID);

		assertThat(target.getFailedLoginAttempts()).isZero();
		assertThat(target.getLockedUntil()).isNull();
	}

	@Test
	@DisplayName("resetting a password signs the user out everywhere")
	void resetPasswordRevokesSessions() {
		User target = user("user-9", Role.USER, UserStatus.ACTIVE);

		this.service.resetPassword("user-9", "BrandNew123");

		assertThat(this.passwordEncoder.matches("BrandNew123", target.getPasswordHash())).isTrue();
		verify(this.refreshTokenService).revokeAllForUser("user-9");
	}

	@Test
	@DisplayName("an unknown user id is reported, not silently ignored")
	void unknownUserIsReported() {
		given(this.userRepository.findById(anyString())).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.get("ghost")).isInstanceOf(UserNotFoundException.class);
		assertThatThrownBy(() -> this.service.deactivate("ghost", ADMIN_ID))
			.isInstanceOf(UserNotFoundException.class);
	}

}
