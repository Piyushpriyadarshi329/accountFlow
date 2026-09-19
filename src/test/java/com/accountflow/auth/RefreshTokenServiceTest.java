package com.accountflow.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.accountflow.auth.domain.RefreshToken;
import com.accountflow.auth.repository.RefreshTokenRepository;
import com.accountflow.auth.service.RefreshTokenService;
import com.accountflow.common.exception.AuthException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.security.JwtProperties;
import com.accountflow.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository repository;

	private RefreshTokenService service;

	@BeforeEach
	void setUp() {
		JwtTokenProvider provider = new JwtTokenProvider(
				new JwtProperties("unit-test-secret-key-long-enough-for-hs256-0123456789", Duration.ofMinutes(15),
						Duration.ofDays(7), "accountflow"));
		this.service = new RefreshTokenService(this.repository, provider);
	}

	@Test
	@DisplayName("stores only a hash, never the raw token")
	void storesOnlyHash() {
		given(this.repository.save(any(RefreshToken.class))).willAnswer((i) -> i.getArgument(0));

		String raw = this.service.issue("user-1", "fam-1", "ip", "agent");

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(this.repository).save(captor.capture());
		assertThat(captor.getValue().getTokenHash()).isNotEqualTo(raw).hasSize(64);
	}

	@Test
	@DisplayName("replaying a rotated token revokes the whole family")
	void reuseRevokesFamily() {
		RefreshToken revoked = RefreshToken.builder()
			.userId("user-1")
			.family("fam-1")
			.revoked(true)
			.expiresAt(Instant.now().plusSeconds(600))
			.build();
		given(this.repository.findByTokenHash(anyString())).willReturn(java.util.Optional.of(revoked));
		given(this.repository.findByFamilyAndRevokedFalse("fam-1"))
			.willReturn(List.of(RefreshToken.builder().family("fam-1").expiresAt(Instant.now()).build()));

		assertThatThrownBy(() -> this.service.consume("stolen-token")).isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED);

		verify(this.repository).findByFamilyAndRevokedFalse("fam-1");
	}

	@Test
	@DisplayName("rejects an expired token")
	void rejectsExpired() {
		given(this.repository.findByTokenHash(anyString())).willReturn(java.util.Optional
			.of(RefreshToken.builder().userId("u").family("f").expiresAt(Instant.now().minusSeconds(1)).build()));

		assertThatThrownBy(() -> this.service.consume("old")).isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TOKEN_EXPIRED);
	}

	@Test
	@DisplayName("rejects an unknown token")
	void rejectsUnknown() {
		given(this.repository.findByTokenHash(anyString())).willReturn(java.util.Optional.empty());

		assertThatThrownBy(() -> this.service.consume("nope")).isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TOKEN_INVALID);
	}

	@Test
	@DisplayName("rotation revokes the old token and links it to its replacement")
	void rotationLinksTokens() {
		RefreshToken current = RefreshToken.builder()
			.userId("user-1")
			.family("fam-1")
			.expiresAt(Instant.now().plusSeconds(600))
			.build();
		given(this.repository.save(any(RefreshToken.class))).willAnswer((i) -> i.getArgument(0));

		String rotated = this.service.rotate(current, "ip", "agent");

		assertThat(current.isRevoked()).isTrue();
		assertThat(current.getReplacedByHash()).isNotNull();
		assertThat(rotated).isNotBlank();
	}

}
