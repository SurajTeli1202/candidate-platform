package com.suraj.candidates.candidate.dto;

import com.suraj.candidates.candidate.CandidateStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Request body for creating (POST) and updating (PUT) a candidate.
 * Max lengths match the column definitions in V1__init.sql.
 * {@code status} is optional: NEW on create, unchanged on update.
 * {@code version} is required on update only (validated with {@link OnUpdate}) and ignored on create.
 */
public record CandidateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @Size(max = 150) String currentTitle,
        @Size(max = 150) String currentCompany,
        @Size(max = 150) String location,
        @DecimalMin("0") @Digits(integer = 3, fraction = 1) BigDecimal experienceYears,
        @PositiveOrZero Integer noticePeriodDays,
        CandidateStatus status,
        @Size(max = 4000) String notes,
        @Size(max = 50) Set<@NotBlank @Size(min = 1, max = 50) String> skills,
        @NotNull(groups = OnUpdate.class) Long version) {

    // Trim before Bean Validation runs, so " jane@example.com " is accepted; the mapper lowercases it.
    public CandidateRequest {
        email = email == null ? null : email.strip();
    }

    /** Validation group for updates; extends Default so all other constraints still apply. */
    public interface OnUpdate extends Default {
    }
}
