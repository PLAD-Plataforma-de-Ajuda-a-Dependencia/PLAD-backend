package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "One page of results")
public record PageResponseDTO<T>(
        @ArraySchema(arraySchema = @Schema(description = "Items of this page"))
        List<T> items,
        @Schema(description = "Zero-based page number", example = "0")
        int page,
        @Schema(description = "Requested page size", example = "20")
        int size,
        @Schema(description = "Total items across all pages", example = "42")
        long totalElements,
        @Schema(description = "Total number of pages", example = "3")
        int totalPages
) {

    public static <E, T> PageResponseDTO<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponseDTO<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
