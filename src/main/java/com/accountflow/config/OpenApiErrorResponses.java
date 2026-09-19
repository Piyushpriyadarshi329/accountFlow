package com.accountflow.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.accountflow.common.exception.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Attaches the failure responses every endpoint can actually produce, so the
 * documented contract matches the {@code GlobalExceptionHandler} rather than
 * showing only the happy path.
 *
 * <p>Applied over the finished document instead of annotating 25 methods, which
 * also means each example can quote the endpoint's own path. What applies where
 * is derived from the operation itself: an operation with an empty security
 * list is public and cannot return 401, and one carrying an
 * {@code Idempotency-Key} is a money endpoint and gets the money failures.
 *
 * <p>Responses a controller documented explicitly are never overwritten.
 */
@Configuration
public class OpenApiErrorResponses {

	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

	private static final Instant SAMPLE_TIME = Instant.parse("2026-09-19T10:30:00Z");

	@Bean
	OpenApiCustomizer standardErrorResponses() {
		return (openApi) -> openApi.getPaths().forEach((path, pathItem) -> {
			for (Operation operation : readOperations(pathItem)) {
				decorate(operation, path);
			}
		});
	}

	private static Iterable<Operation> readOperations(PathItem pathItem) {
		return pathItem.readOperations();
	}

	private void decorate(Operation operation, String path) {
		ApiResponses responses = operation.getResponses();
		if (responses == null) {
			return;
		}

		if (!isPublic(operation)) {
			put(responses, "401", "Missing, expired or revoked access token", ErrorCode.UNAUTHENTICATED,
					"Authentication is required", path);
		}
		if (operation.getRequestBody() != null) {
			put(responses, "400", "Request body failed validation", ErrorCode.VALIDATION_ERROR,
					"Request validation failed", path);
		}
		if (hasParameter(operation, "header", IDEMPOTENCY_HEADER)) {
			put(responses, "400", "Missing Idempotency-Key, or an amount that is not positive or is more "
					+ "precise than the currency allows", ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
					"An Idempotency-Key header is required for money-moving requests", path);
			put(responses, "409", "A request with this key is still in flight; retry shortly",
					ErrorCode.REQUEST_IN_PROGRESS, "This account is busy with another request; please retry", path);
			put(responses, "422",
					"A business rule rejected it: INSUFFICIENT_BALANCE, ACCOUNT_CLOSED, CURRENCY_MISMATCH, "
							+ "or IDEMPOTENCY_KEY_REUSED when the key was reused with a different body",
					ErrorCode.INSUFFICIENT_BALANCE, "Insufficient account balance", path);
		}
		if (hasParameter(operation, "path", null)) {
			put(responses, "404", "No such resource - also returned when it exists but belongs to another "
					+ "user, so the API cannot be used to discover ids", ErrorCode.ACCOUNT_NOT_FOUND,
					"Account not found", path);
		}
		put(responses, "500", "Unexpected server error", ErrorCode.INTERNAL_ERROR, "An unexpected error occurred",
				path);
	}

	/** springdoc emits an empty security list for endpoints marked public. */
	private static boolean isPublic(Operation operation) {
		return operation.getSecurity() != null && operation.getSecurity().isEmpty();
	}

	private static boolean hasParameter(Operation operation, String in, String name) {
		return operation.getParameters() != null && operation.getParameters()
			.stream()
			.anyMatch((parameter) -> in.equals(parameter.getIn())
					&& (name == null || name.equalsIgnoreCase(parameter.getName())));
	}

	private static void put(ApiResponses responses, String status, String description, ErrorCode code,
			String message, String path) {
		if (responses.containsKey(status)) {
			return;
		}
		// A Map, not a String: Swagger UI renders a string example as an escaped
		// blob, while a map renders as the JSON body a caller would actually see.
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("success", false);
		body.put("code", code.name());
		body.put("message", message);
		body.put("timestamp", SAMPLE_TIME.toString());
		body.put("path", path);
		body.put("requestId", "0f9c1a52-8f1e-4a7b-9a3d-2c6b5e4d1a88");
		Example example = new Example().value(body);

		responses.addApiResponse(status,
				new ApiResponse().description(description)
					.content(new Content().addMediaType("application/json",
							new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA))
								.addExamples("default", example))));
	}

}
