package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateRequest;
import com.suraj.candidates.candidate.dto.CandidateResponse;
import com.suraj.candidates.candidate.dto.CandidateSearchCriteria;
import com.suraj.candidates.candidate.dto.PageResponse;
import com.suraj.candidates.event.CandidateEvent;
import com.suraj.candidates.event.CandidateEventPublisher;
import com.suraj.candidates.event.CandidateEventType;
import com.suraj.candidates.skill.Skill;
import com.suraj.candidates.skill.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Entities are mapped to DTOs inside the transaction because open-in-view is disabled
 * (lazy skills must be loaded before the session closes). Logs contain IDs only, never PII.
 */
@Service
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final CandidateRepository candidateRepository;
    private final SkillRepository skillRepository;
    private final CandidateMapper mapper;
    private final CandidateEventPublisher eventPublisher;

    public CandidateService(CandidateRepository candidateRepository, SkillRepository skillRepository,
                            CandidateMapper mapper, CandidateEventPublisher eventPublisher) {
        this.candidateRepository = candidateRepository;
        this.skillRepository = skillRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CandidateResponse create(CandidateRequest request) {
        if (candidateRepository.countByEmailIncludingDeleted(mapper.normalizeEmail(request.email())) > 0) {
            throw new DuplicateEmailException();
        }
        Candidate candidate = mapper.toNewEntity(request);
        candidate.replaceSkills(resolveSkills(request.skills()));
        Candidate saved = candidateRepository.saveAndFlush(candidate);
        eventPublisher.publish(CandidateEvent.from(CandidateEventType.CANDIDATE_CREATED, saved, MDC.get("requestId")));
        log.info("Created candidate {}", saved.getId());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateResponse> search(CandidateSearchCriteria criteria) {
        return PageResponse.from(candidateRepository.findAll(
                CandidateSpecifications.from(criteria), criteria.toPageable()).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public CandidateResponse getById(Long id) {
        return mapper.toResponse(findCandidate(id));
    }

    @Transactional
    public CandidateResponse update(Long id, CandidateRequest request) {
        Candidate candidate = findCandidate(id);
        if (!Objects.equals(candidate.getVersion(), request.version())) {
            throw new ObjectOptimisticLockingFailureException(Candidate.class, id);
        }
        if (candidateRepository.countByEmailIncludingDeletedExcludingId(mapper.normalizeEmail(request.email()), id) > 0) {
            throw new DuplicateEmailException();
        }
        mapper.applyTo(request, candidate);
        candidate.replaceSkills(resolveSkills(request.skills()));
        // Flush now so the response contains the incremented version and the new updatedAt.
        Candidate saved = candidateRepository.saveAndFlush(candidate);
        eventPublisher.publish(CandidateEvent.from(CandidateEventType.CANDIDATE_UPDATED, saved, MDC.get("requestId")));
        log.info("Updated candidate {}", id);
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Candidate candidate = findCandidate(id);
        candidate.markDeleted();
        Candidate saved = candidateRepository.saveAndFlush(candidate);
        eventPublisher.publish(CandidateEvent.from(CandidateEventType.CANDIDATE_DELETED, saved, MDC.get("requestId")));
        log.info("Soft-deleted candidate {}", id);
    }

    private Candidate findCandidate(Long id) {
        return candidateRepository.findById(id).orElseThrow(() -> new CandidateNotFoundException(id));
    }

    // Two concurrent requests creating the same new skill can still hit uq_skill_name;
    // that DataIntegrityViolationException is mapped to 409.
    private Set<Skill> resolveSkills(Set<String> requestedNames) {
        Set<String> names = mapper.normalizeSkillNames(requestedNames);
        if (names.isEmpty()) {
            return Set.of();
        }
        Set<Skill> skills = new HashSet<>(skillRepository.findByNameIn(names));
        for (String name : names) {
            Skill skill = new Skill(name);
            if (!skills.contains(skill)) {
                skills.add(skillRepository.save(skill));
            }
        }
        return skills;
    }
}
