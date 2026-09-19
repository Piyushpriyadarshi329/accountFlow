package com.accountflow.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.accountflow.auth.domain.RefreshToken;
import com.accountflow.auth.dto.LoginRequest;
import com.accountflow.auth.dto.RegisterRequest;
import com.accountflow.auth.dto.TokenResponse;
import com.accountflow.common.exception.AuthException;
import com.accountflow.common.exception.DuplicateEmailException;
import com.accountflow.common.exception.DuplicatePhoneException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.security.JwtTokenProvider;
import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import com.accountflow.user.mapper.UserMapper;
import com.accountflow.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private static final int MAX_FAILED_ATTEMPTS = 5;

	private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

	private final UserRepository userRepository;

	private final RefreshTokenService refreshTokenService;

	private final JwtTokenProvider tokenProvider;

	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, RefreshTokenService refreshTokenService,
			JwtTokenProvider tokenProvider, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.refreshTokenService = refreshTokenService;
		this.tokenProvider = tokenProvider;
		this.passwordEncoder = passwordEncoder;
	}

	public TokenResponse register(RegisterRequest request, String ipAddress, String userAgent) {
		String email = normalizeEmail(request.email());
		String phone = (request.phone() != null && !request.phone().isBlank()) ? request.phone().trim() : null;

		if (this.userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}
		if (phone != null && this.userRepository.existsByPhone(phone)) {
			throw new DuplicatePhoneException();
		}

		User user = User.builder()
			.firstName(request.firstName().trim())
			.lastName((request.lastName() != null) ? request.lastName().trim() : null)
			.email(email)
			.phone(phone)
			.passwordHash(this.passwordEncoder.encode(request.password()))
			.role(Role.USER)
			.status(UserStatus.ACTIVE)
			.build();

		try {
			user = this.userRepository.save(user);
		}
		catch (DuplicateKeyException ex) {
			// The pre-checks above race; the unique index is what actually decides.
			throw (ex.getMessage() != null && ex.getMessage().contains("phone")) ? new DuplicatePhoneException()
					: new DuplicateEmailException();
		}

		log.info("Registered user {}", user.getId());
		return issueTokens(user, null, ipAddress, userAgent);
	}

	public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
		String email = normalizeEmail(request.email());
		User user = this.userRepository.findByEmail(email).orElse(null);

		if (user == null) {
			// Hash anyway so a missing account is not detectably faster than a
			// wrong password.
			this.passwordEncoder.encode(request.password());
			throw AuthException.invalidCredentials();
		}
		if (user.isCurrentlyLocked()) {
			throw new AuthException(ErrorCode.USER_LOCKED,
					"Account is temporarily locked due to repeated failed sign-in attempts");
		}
		if (!this.passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			registerFailedAttempt(user);
			throw AuthException.invalidCredentials();
		}
		if (!user.getStatus().canAuthenticate()) {
			throw new AuthException(ErrorCode.USER_INACTIVE, "This account is not active");
		}

		if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
			user.setFailedLoginAttempts(0);
			user.setLockedUntil(null);
			user = this.userRepository.save(user);
		}
		return issueTokens(user, null, ipAddress, userAgent);
	}

	public TokenResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
		RefreshToken consumed = this.refreshTokenService.consume(rawRefreshToken);
		User user = this.userRepository.findById(consumed.getUserId())
			.orElseThrow(() -> new AuthException(ErrorCode.TOKEN_INVALID, "Refresh token is invalid"));
		if (!user.getStatus().canAuthenticate() || user.isCurrentlyLocked()) {
			throw new AuthException(ErrorCode.USER_INACTIVE, "This account is not active");
		}

		String rotated = this.refreshTokenService.rotate(consumed, ipAddress, userAgent);
		return TokenResponse.of(this.tokenProvider.generateAccessToken(user), rotated,
				this.tokenProvider.accessTokenTtl().toSeconds(), UserMapper.toResponse(user));
	}

	/** Revokes every refresh token and invalidates outstanding access tokens. */
	public void logout(String userId) {
		this.refreshTokenService.revokeAllForUser(userId);
		this.userRepository.findById(userId).ifPresent((user) -> {
			user.setTokenVersion(user.getTokenVersion() + 1);
			this.userRepository.save(user);
		});
		log.info("Logged out user {}", userId);
	}

	private TokenResponse issueTokens(User user, String family, String ipAddress, String userAgent) {
		String refreshToken = this.refreshTokenService.issue(user.getId(), family, ipAddress, userAgent);
		return TokenResponse.of(this.tokenProvider.generateAccessToken(user), refreshToken,
				this.tokenProvider.accessTokenTtl().toSeconds(), UserMapper.toResponse(user));
	}

	private void registerFailedAttempt(User user) {
		int attempts = user.getFailedLoginAttempts() + 1;
		user.setFailedLoginAttempts(attempts);
		if (attempts >= MAX_FAILED_ATTEMPTS) {
			user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
			log.warn("User {} locked after {} failed sign-in attempts", user.getId(), attempts);
		}
		this.userRepository.save(user);
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
