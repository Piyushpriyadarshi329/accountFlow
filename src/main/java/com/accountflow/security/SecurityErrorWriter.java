package com.accountflow.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.accountflow.common.api.ErrorResponse;
import com.accountflow.common.exception.ErrorCode;
import com.accountflow.common.web.RequestIdFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Filter-chain failures happen before {@code @RestControllerAdvice} can see
 * them, so they are serialized here to keep one error format across the API.
 *
 * <p>Uses Jackson 3's {@code tools.jackson.databind.ObjectMapper} - the type
 * Spring Boot 4 auto-configures. Jackson 2 is still on the classpath
 * transitively, and injecting its ObjectMapper fails at startup.
 */
@Component
public class SecurityErrorWriter {

	private final ObjectMapper objectMapper;

	public SecurityErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
			throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ErrorResponse body = ErrorResponse.of(code.name(), message, request.getRequestURI(),
				RequestIdFilter.currentRequestId(), null);
		this.objectMapper.writeValue(response.getOutputStream(), body);
	}

}
