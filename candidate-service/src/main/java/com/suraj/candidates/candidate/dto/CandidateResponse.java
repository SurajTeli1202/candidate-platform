package com.suraj.candidates.candidate.dto;

import com.suraj.candidates.candidate.CandidateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CandidateResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String currentTitle,
        String currentCompany,
        String location,
        BigDecimal experienceYears,
        Integer noticePeriodDays,
        CandidateStatus status,
        String notes,
        List<String> skills,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
