package com.accountflow.auth;

import java.time.Duration;
import java.time.Instant;

import com.accountflow.auth.domain.RefreshToken;
import com.accountflow.auth.dto.LoginRequest;
import com.accountflow.auth.dto.RegisterRequest;
import com.accountflow.auth.service.AuthService;
import com.accountflow.auth.service.RefreshTokenService;
import com.accountflow.common.exception.AuthException;
import com.accountflow.common.exception.DuplicateEmailException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.security.JwtTokenProvider;
import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import com.accountflow.user.repository.UserRepository;
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
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RefreshTokenService refreshTokenService;

	private AuthService authService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // fast for tests

	private final JwtTokenProvider tokenProvider = new JwtTokenProvider(new com.accountflow.security.JwtProperties(
			"unit-test-secret-key-long-enough-for-hs256-0123456789", Duration.ofMinutes(15), Duration.ofDays(7),
			"accountflow"));

	@BeforeEach
	void setUp() {
		this.authService = new AuthService(this.userRepository, this.refreshTokenService, this.tokenProvider,
				this.passwordEncoder);
		given(this.refreshTokenService.issue(anyString(), any(), any(), any())).willReturn("refresh-token");
	}

	private static User activeUser(PasswordEncoder encoder, String rawPassword) {
		return User.builder()
			.id("user-1")
			.firstName("Piyush")
			.email("piyush@example.com")
			.passwordHash(encoder.encode(rawPassword))
			.role(Role.USER)
			.status(UserStatus.ACTIVE)
			.build();
	}

	@Test
	@DisplayName("register hashes the password and never persists it in plain text")
	void registerHashesPassword() {
		given(this.userRepository.existsByEmail(anyString())).willReturn(false);
		given(this.userRepository.save(any(User.class))).willAnswer((invocation) -> {
			User saved = invocation.getArgument(0);
			saved.setId("user-1");
			return saved;
		});

		this.authService.register(new RegisterRequest("Piyush", "P", "Piyush@Example.com ", null, "Secret123"),
				"127.0.0.1", "junit");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(this.userRepository).save(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getPasswordHash()).isNotEqualTo("Secret123").startsWith("$2");
		assertThat(this.passwordEncoder.matches("Secret123", saved.getPasswordHash())).isTrue();
	}

	@Test
	@DisplayName("register normalizes the email so uniqueness is case-insensitive")
	void registerNormalizesEmail() {
		given(this.userRepository.existsByEmail(anyString())).willReturn(false);
		given(this.userRepository.save(any(User.class))).willAnswer((invocation) -> {
			User saved = invocation.getArgument(0);
			saved.setId("user-1");
			return saved;
		});

		this.authService.register(new RegisterRequest("Piyush", null, "  Piyush@Example.COM ", null, "Secret123"),
				"127.0.0.1", "junit");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(this.userRepository).save(captor.capture());
		assertThat(captor.getValue().getEmail()).isEqualTo("piyush@example.com");
	}

	@Test
	@DisplayName("register rejects a duplicate email")
	void registerRejectsDuplicateEmail() {
		given(this.userRepository.existsByEmail("piyush@example.com")).willReturn(true);

		assertThatThrownBy(() -> this.authService
			.register(new RegisterRequest("Piyush", null, "piyush@example.com", null, "Secret123"), "127.0.0.1", "j"))
			.isInstanceOf(DuplicateEmailException.class);
		verify(this.userRepository, never()).save(any());
	}

	@Test
	@DisplayName("login succeeds with correct credentials")
	void loginSucceeds() {
		given(this.userRepository.findByEmail("piyush@example.com"))
			.willReturn(java.util.Optional.of(activeUser(this.passwordEncoder, "Secret123")));

		var response = this.authService.login(new LoginRequest("piyush@example.com", "Secret123"), "127.0.0.1", "j");

		assertThat(response.accessToken()).isNotBlank();
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.user().email()).isEqualTo("piyush@example.com");
	}

	@Test
	@DisplayName("an unknown email and a wrong password fail identically - no user enumeration")
	void doesNotLeakWhetherAccountExists() {
		given(this.userRepository.findByEmail("nobody@example.com")).willReturn(java.util.Optional.empty());
		given(this.userRepository.findByEmail("piyush@example.com"))
			.willReturn(java.util.Optional.of(activeUser(this.passwordEncoder, "Secret123")));

		Throwable unknownEmail = org.assertj.core.api.Assertions
			.catchThrowable(() -> this.authService.login(new LoginRequest("nobody@example.com", "x"), "ip", "j"));
		Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
				() -> this.authService.login(new LoginRequest("piyush@example.com", "WrongPass1"), "ip", "j"));

		assertThat(unknownEmail).isInstanceOf(AuthException.class);
		assertThat(wrongPassword).isInstanceOf(AuthException.class);
		assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
		assertThat(((AuthException) unknownEmail).errorCode())
			.isEqualTo(((AuthException) wrongPassword).errorCode());
	}

	@Test
	@DisplayName("locks the account after five failed attempts")
	void locksAfterFiveFailedAttempts() {
		User user = activeUser(this.passwordEncoder, "Secret123");
		user.setFailedLoginAttempts(4);
		given(this.userRepository.findByEmail("piyush@example.com")).willReturn(java.util.Optional.of(user));
		given(this.userRepository.save(any(User.class))).willAnswer((i) -> i.getArgument(0));

		assertThatThrownBy(
				() -> this.authService.login(new LoginRequest("piyush@example.com", "Wrong1234"), "ip", "j"))
			.isInstanceOf(AuthException.class);

		assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
		assertThat(user.getLockedUntil()).isNotNull().isAfter(Instant.now());
	}

	@Test
	@DisplayName("refuses sign-in while locked, even with the right password")
	void refusesWhileLocked() {
		User user = activeUser(this.passwordEncoder, "Secret123");
		user.setLockedUntil(Instant.now().plusSeconds(600));
		given(this.userRepository.findByEmail("piyush@example.com")).willReturn(java.util.Optional.of(user));

		assertThatThrownBy(
				() -> this.authService.login(new LoginRequest("piyush@example.com", "Secret123"), "ip", "j"))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.USER_LOCKED);
	}

	@Test
	@DisplayName("refuses sign-in for an inactive account")
	void refusesInactiveAccount() {
		User user = activeUser(this.passwordEncoder, "Secret123");
		user.setStatus(UserStatus.INACTIVE);
		given(this.userRepository.findByEmail("piyush@example.com")).willReturn(java.util.Optional.of(user));

		assertThatThrownBy(
				() -> this.authService.login(new LoginRequest("piyush@example.com", "Secret123"), "ip", "j"))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.USER_INACTIVE);
	}

	@Test
	@DisplayName("a successful sign-in clears the failed-attempt counter")
	void successfulLoginResetsCounter() {
		User user = activeUser(this.passwordEncoder, "Secret123");
		user.setFailedLoginAttempts(3);
		given(this.userRepository.findByEmail("piyush@example.com")).willReturn(java.util.Optional.of(user));
		given(this.userRepository.save(any(User.class))).willAnswer((i) -> i.getArgument(0));

		this.authService.login(new LoginRequest("piyush@example.com", "Secret123"), "ip", "j");

		assertThat(user.getFailedLoginAttempts()).isZero();
	}

	@Test
	@DisplayName("logout revokes refresh tokens and bumps tokenVersion so live access tokens die")
	void logoutRevokesEverything() {
		User user = activeUser(this.passwordEncoder, "Secret123");
		user.setTokenVersion(2);
		given(this.userRepository.findById("user-1")).willReturn(java.util.Optional.of(user));
		given(this.userRepository.save(any(User.class))).willAnswer((i) -> i.getArgument(0));

		this.authService.logout("user-1");

		verify(this.refreshTokenService).revokeAllForUser("user-1");
		assertThat(user.getTokenVersion()).isEqualTo(3);
	}

	@Test
	@DisplayName("refresh rotates the token and refuses a deactivated user")
	void refreshRotatesAndChecksStatus() {
		RefreshToken stored = RefreshToken.builder()
			.userId("user-1")
			.family("fam-1")
			.expiresAt(Instant.now().plusSeconds(600))
			.build();
		User user = activeUser(this.passwordEncoder, "Secret123");
		given(this.refreshTokenService.consume("raw")).willReturn(stored);
		given(this.refreshTokenService.rotate(stored, "ip", "j")).willReturn("rotated-token");
		given(this.userRepository.findById("user-1")).willReturn(java.util.Optional.of(user));

		assertThat(this.authService.refresh("raw", "ip", "j").refreshToken()).isEqualTo("rotated-token");

		user.setStatus(UserStatus.LOCKED);
		assertThatThrownBy(() -> this.authService.refresh("raw", "ip", "j")).isInstanceOf(AuthException.class);
	}

}
