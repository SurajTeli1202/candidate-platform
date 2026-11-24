package com.suraj.candidates.candidate.dto;

import com.suraj.candidates.candidate.CandidateStatus;
import com.suraj.candidates.candidate.SkillMatch;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Set;

public record CandidateSearchCriteria(
        @Parameter(description = "Up to 100 characters and 5 tokens. Each matches name, email, title or company; "
                + "case-insensitive. %, _ and ! are literal.")
        @Size(max = 100)
        @Pattern(regexp = "\\s*(?:\\S+\\s+){0,4}\\S*\\s*",
                message = "must contain at most 5 whitespace-separated tokens")
        String q,
        @Parameter(description = "Match any status; repeated or comma-separated values.")
        Set<@NotNull CandidateStatus> status,
        @Parameter(description = "Case-insensitive exact match; surrounding whitespace is trimmed.")
        @Size(max = 150) String location,
        @Parameter(description = "Up to 20 comma-separated names of at most 100 characters each; trimmed, lowercased and deduplicated.")
        @Size(max = 20) Set<@NotBlank @Size(max = 100) String> skills,
        @Parameter(description = "Require all or any requested skills.", schema = @Schema(defaultValue = "ALL"))
        SkillMatch skillMatch,
        @Parameter(description = "Inclusive minimum experience; must not exceed maxExperience.")
        @DecimalMin("0") BigDecimal minExperience,
        @Parameter(description = "Inclusive maximum experience.")
        @DecimalMin("0") BigDecimal maxExperience,
        @Parameter(description = "Inclusive maximum notice period in days.")
        @Min(0) Integer maxNoticePeriod,
        @Parameter(description = "Zero-based page number; page * size must not exceed 100000. Narrow the search for larger offsets.",
                schema = @Schema(type = "integer", format = "int32", defaultValue = "0"))
        @Min(0) Integer page,
        @Parameter(description = "Page size, from 1 to 100.",
                schema = @Schema(type = "integer", format = "int32", defaultValue = "20"))
        @Min(1) @Max(100) Integer size,
        @Parameter(description = "field,asc|desc. Fields: createdAt, updatedAt, firstName, lastName, experienceYears, status. "
                + "Nulls last, then id ascending.", schema = @Schema(defaultValue = "createdAt,desc"))
        @Pattern(
                regexp = "(createdAt|updatedAt|firstName|lastName|experienceYears|status),(?i:asc|desc)",
                message = "must be an allowed field followed by ,asc or ,desc")
        String sort) {

    public CandidateSearchCriteria {
        status = status == null ? Set.of() : status;
        skills = skills == null ? Set.of() : skills;
        skillMatch = skillMatch == null ? SkillMatch.ALL : skillMatch;
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sort = sort == null ? "createdAt,desc" : sort;
    }

    @AssertTrue(message = "minExperience must not exceed maxExperience")
    @Schema(hidden = true)
    public boolean isExperienceRangeValid() {
        return minExperience == null || maxExperience == null || minExperience.compareTo(maxExperience) <= 0;
    }

    @AssertTrue(message = "page * size must not exceed 100000; narrow the search")
    @Schema(hidden = true)
    public boolean isPageOffsetValid() {
        return (long) page * size <= 100_000;
    }

    public Pageable toPageable() {
        String[] parts = sort.split(",");
        Sort ordering = Sort.by(
                new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]).nullsLast(),
                Sort.Order.asc("id").nullsLast());
        return PageRequest.of(page, size, ordering);
    }
}
