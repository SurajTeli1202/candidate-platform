package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateRequest;
import com.suraj.candidates.candidate.dto.CandidateResponse;
import com.suraj.candidates.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class CandidateMapper {

    public Candidate toNewEntity(CandidateRequest request) {
        Candidate candidate = new Candidate();
        applyTo(request, candidate);
        return candidate;
    }

    /** Copies request fields onto the entity. Skills and version are handled by the service. */
    public void applyTo(CandidateRequest request, Candidate candidate) {
        candidate.setFirstName(request.firstName());
        candidate.setLastName(request.lastName());
        candidate.setEmail(normalizeEmail(request.email()));
        candidate.setPhone(request.phone());
        candidate.setCurrentTitle(request.currentTitle());
        candidate.setCurrentCompany(request.currentCompany());
        candidate.setLocation(request.location());
        candidate.setExperienceYears(request.experienceYears());
        candidate.setNoticePeriodDays(request.noticePeriodDays());
        candidate.setNotes(request.notes());
        if (request.status() != null) {
            candidate.setStatus(request.status());
        }
    }

    public CandidateResponse toResponse(Candidate candidate) {
        return new CandidateResponse(
                candidate.getId(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getEmail(),
                candidate.getPhone(),
                candidate.getCurrentTitle(),
                candidate.getCurrentCompany(),
                candidate.getLocation(),
                candidate.getExperienceYears(),
                candidate.getNoticePeriodDays(),
                candidate.getStatus(),
                candidate.getNotes(),
                candidate.getSkills().stream().map(Skill::getName).sorted().toList(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt(),
                candidate.getVersion());
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Trims, lowercases and de-duplicates skill names; null means no skills. */
    public Set<String> normalizeSkillNames(Set<String> skillNames) {
        if (skillNames == null) {
            return Set.of();
        }
        return skillNames.stream().map(Skill::normalize).collect(Collectors.toCollection(TreeSet::new));
    }
}
