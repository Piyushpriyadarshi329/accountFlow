package com.accountflow.common.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id, exposed to logs via MDC, to callers
 * via the response header, and to response bodies via the API envelope. Honours
 * an inbound X-Request-Id so a trace can span services.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	public static final String MDC_KEY = "requestId";

	public static final String MDC_USER_KEY = "userId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = request.getHeader(HEADER);
		if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
			requestId = UUID.randomUUID().toString();
		}
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
			MDC.remove(MDC_USER_KEY);
		}
	}

	public static String currentRequestId() {
		return MDC.get(MDC_KEY);
	}

}
