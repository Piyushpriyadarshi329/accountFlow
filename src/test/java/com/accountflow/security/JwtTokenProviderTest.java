package com.accountflow.security;

import java.time.Duration;

import com.accountflow.common.exception.AuthException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.user.domain.Role;
import com.accountflow.user.domain.User;
import com.accountflow.user.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

	private static final String SECRET = "unit-test-secret-key-long-enough-for-hs256-0123456789";

	private final JwtTokenProvider provider = new JwtTokenProvider(
			new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "accountflow"));

	private static User user() {
		return User.builder()
			.id("user-1")
			.email("piyush@example.com")
			.role(Role.USER)
			.status(UserStatus.ACTIVE)
			.tokenVersion(3)
			.build();
	}

	@Test
	@DisplayName("round-trips subject, role and token version")
	void roundTripsClaims() {
		var claims = this.provider.parse(this.provider.generateAccessToken(user()));

		assertThat(claims.getSubject()).isEqualTo("user-1");
		assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).isEqualTo("USER");
		assertThat(claims.get(JwtTokenProvider.CLAIM_TOKEN_VERSION, Integer.class)).isEqualTo(3);
		assertThat(claims.getIssuer()).isEqualTo("accountflow");
	}

	@Test
	@DisplayName("rejects a token signed with a different key")
	void rejectsForeignSignature() {
		JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(
				"a-completely-different-secret-key-also-long-enough-123456", Duration.ofMinutes(15),
				Duration.ofDays(7), "accountflow"));
		String foreign = other.generateAccessToken(user());

		assertThatThrownBy(() -> this.provider.parse(foreign)).isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TOKEN_INVALID);
	}

	@Test
	@DisplayName("rejects a tampered payload")
	void rejectsTamperedToken() {
		String token = this.provider.generateAccessToken(user());
		String tampered = token.substring(0, token.lastIndexOf('.')) + ".AAAA";

		assertThatThrownBy(() -> this.provider.parse(tampered)).isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("reports expiry distinctly from invalidity")
	void reportsExpiryDistinctly() {
		JwtTokenProvider expiring = new JwtTokenProvider(
				new JwtProperties(SECRET, Duration.ofSeconds(-1), Duration.ofDays(7), "accountflow"));
		String expired = expiring.generateAccessToken(user());

		assertThatThrownBy(() -> this.provider.parse(expired)).isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.TOKEN_EXPIRED);
	}

	@Test
	@DisplayName("refuses a signing key shorter than HS256 requires")
	void refusesShortSecret() {
		assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofMinutes(15), Duration.ofDays(7), "x"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 bytes");
	}

}
