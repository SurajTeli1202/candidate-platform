package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateRequest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateMapperTest {

    private final CandidateMapper mapper = new CandidateMapper();

    @Test
    void applyToKeepsExistingStatusWhenNullAndOverwritesItWhenGiven() {
        Candidate candidate = new Candidate("Jane", "Doe", "jane@example.com");
        candidate.setStatus(CandidateStatus.INTERVIEWING);

        mapper.applyTo(requestWithStatus(null), candidate);

        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.INTERVIEWING);

        mapper.applyTo(requestWithStatus(CandidateStatus.OFFERED), candidate);

        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.OFFERED);
    }

    @Test
    void normalizeSkillNamesTrimsLowercasesDeduplicatesAndReturnsEmptySetForNull() {
        assertThat(mapper.normalizeSkillNames(Set.of("  JaVa ", "JAVA", " Spring ")))
                .containsExactlyInAnyOrder("java", "spring");
        assertThat(mapper.normalizeSkillNames(null)).isEmpty();
    }

    private static CandidateRequest requestWithStatus(CandidateStatus status) {
        return new CandidateRequest("Jane", "Doe", "jane@example.com",
                null, null, null, null, null, null, status, null, null, null);
    }
}
