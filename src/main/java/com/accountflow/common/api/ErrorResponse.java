package com.accountflow.common.api;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ErrorResponse", description = "The shape of every failure. Branch on `code`, which is "
		+ "stable; `message` is for humans and may change.")
public record ErrorResponse(@Schema(example = "false") boolean success,

		@Schema(description = "Stable machine-readable error code.", example = "INSUFFICIENT_BALANCE")
		String code,

		@Schema(example = "Insufficient account balance") String message,

		@Schema(example = "2026-09-19T10:30:00Z") Instant timestamp,

		@Schema(example = "/api/v1/accounts/6aae292c/transactions/debit") String path,

		@Schema(description = "Also returned in the X-Request-Id response header.",
				example = "0f9c1a52-8f1e-4a7b-9a3d-2c6b5e4d1a88") String requestId,

		@Schema(description = "Present only on VALIDATION_ERROR: the offending field, and why.",
				example = """
						{"amount":"Amount must be greater than zero"}""") Map<String, String> fieldErrors) {

	public static ErrorResponse of(String code, String message, String path, String requestId,
			Map<String, String> fieldErrors) {
		return new ErrorResponse(false, code, message, Instant.now(), path, requestId, fieldErrors);
	}

}
