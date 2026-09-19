package com.accountflow.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.accountflow.common.exception.AuthException;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.web.RequestIdFilter;
import com.accountflow.user.domain.User;
import com.accountflow.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifies the bearer token and establishes the security context.
 *
 * <p>The user is re-read per request so a locked or deactivated account loses
 * access immediately, and so {@code tokenVersion} can revoke outstanding
 * tokens. That costs one indexed lookup per call; if it ever shows up in a
 * profile, cache it with a short TTL rather than dropping the check.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	private final UserRepository userRepository;

	private final SecurityErrorWriter errorWriter;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository,
			SecurityErrorWriter errorWriter) {
		this.tokenProvider = tokenProvider;
		this.userRepository = userRepository;
		this.errorWriter = errorWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith(BEARER)) {
			chain.doFilter(request, response);
			return;
		}

		try {
			Claims claims = this.tokenProvider.parse(header.substring(BEARER.length()).trim());
			User user = this.userRepository.findById(claims.getSubject())
				.orElseThrow(() -> new AuthException(ErrorCode.TOKEN_INVALID, "Access token is invalid"));

			if (!user.getStatus().canAuthenticate() || user.isCurrentlyLocked()) {
				throw new AuthException(ErrorCode.USER_INACTIVE, "This account is not active");
			}
			Integer tokenVersion = claims.get(JwtTokenProvider.CLAIM_TOKEN_VERSION, Integer.class);
			if (tokenVersion == null || tokenVersion != user.getTokenVersion()) {
				throw new AuthException(ErrorCode.TOKEN_INVALID, "Access token has been revoked");
			}

			AuthenticatedUser principal = AuthenticatedUser.from(user);
			var authentication = new UsernamePasswordAuthenticationToken(principal, null,
					List.of(new SimpleGrantedAuthority(user.getRole().authority())));
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			MDC.put(RequestIdFilter.MDC_USER_KEY, user.getId());

			chain.doFilter(request, response);
		}
		catch (AuthException ex) {
			SecurityContextHolder.clearContext();
			this.errorWriter.write(request, response, ex.errorCode(), ex.getMessage());
		}
	}

}
