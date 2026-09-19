package com.accountflow.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.accountflow.common.api.ErrorResponse;
import com.accountflow.common.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single place that turns exceptions into the wire format. Expected failures
 * keep their stable code; anything unexpected is logged with its request id and
 * reported as a generic 500, so internals never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
		log.debug("Business rule rejected request: {} - {}", ex.errorCode(), ex.getMessage());
		return build(ex.errorCode(), ex.getMessage(), request, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		return build(ErrorCode.MALFORMED_REQUEST, "Request body is missing or malformed", request, null);
	}

	/**
	 * Safety net for a unique-index violation that no service anticipated - for
	 * example two postings colliding on a transaction reference. Better a stable
	 * 409 than a 500 leaking the index name.
	 */
	@ExceptionHandler(DuplicateKeyException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
		log.warn("Unique index violation on {} {}", request.getMethod(), request.getRequestURI());
		return build(ErrorCode.DUPLICATE_TRANSACTION, "This operation conflicts with an existing record", request,
				null);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return build(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", request, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, null);
	}

	private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, HttpServletRequest request,
			Map<String, String> fieldErrors) {
		ErrorResponse body = ErrorResponse.of(code.name(), message, request.getRequestURI(),
				RequestIdFilter.currentRequestId(), fieldErrors);
		return ResponseEntity.status(code.status()).body(body);
	}

}
