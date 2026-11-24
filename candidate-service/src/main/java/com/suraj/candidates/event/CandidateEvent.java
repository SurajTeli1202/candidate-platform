package com.suraj.candidates.event;

import com.suraj.candidates.candidate.Candidate;
import com.suraj.candidates.candidate.CandidateStatus;
import com.suraj.candidates.skill.Skill;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidateEvent(
        UUID eventId,
        CandidateEventType eventType,
        Instant occurredAt,
        Long candidateId,
        Long version,
        String requestId,
        CandidatePayload payload
) {
    public record CandidatePayload(
            String email,
            String firstName,
            String lastName,
            CandidateStatus status,
            List<String> skills
    ) {
    }

    public static CandidateEvent from(CandidateEventType eventType, Candidate candidate, String requestId) {
        List<String> skillNames = candidate.getSkills().stream()
                .map(Skill::getName)
                .sorted()
                .toList();

        CandidatePayload payload = new CandidatePayload(
                candidate.getEmail(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getStatus(),
                skillNames
        );

        return new CandidateEvent(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                candidate.getId(),
                candidate.getVersion(),
                requestId,
                payload
        );
    }
}
