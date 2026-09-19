package com.accountflow.common.api;

import com.accountflow.common.web.RequestIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope for every successful response. Failures go through
 * {@link com.accountflow.common.api.ErrorResponse} instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, PaginationMeta pagination, String requestId) {

	public static <T> ApiResponse<T> of(T data, String message) {
		return new ApiResponse<>(true, data, message, null, RequestIdFilter.currentRequestId());
	}

	public static <T> ApiResponse<T> of(T data) {
		return of(data, null);
	}

	public static <T> ApiResponse<T> paged(T data, PaginationMeta pagination) {
		return new ApiResponse<>(true, data, null, pagination, RequestIdFilter.currentRequestId());
	}

}
