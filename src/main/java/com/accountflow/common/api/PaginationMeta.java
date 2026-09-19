package com.accountflow.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

@Schema(description = "Paging detail, returned alongside `data` on list endpoints.")
public record PaginationMeta(@Schema(example = "0") int page, @Schema(example = "20") int size,
		@Schema(example = "100") long totalElements, @Schema(example = "5") int totalPages) {

	public static PaginationMeta from(Page<?> page) {
		return new PaginationMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

}
