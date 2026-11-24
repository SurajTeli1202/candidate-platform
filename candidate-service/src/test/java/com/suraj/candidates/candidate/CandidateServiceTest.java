package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateRequest;
import com.suraj.candidates.candidate.dto.CandidateResponse;
import com.suraj.candidates.candidate.dto.CandidateSearchCriteria;
import com.suraj.candidates.candidate.dto.PageResponse;
import com.suraj.candidates.skill.Skill;
import com.suraj.candidates.skill.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private SkillRepository skillRepository;

    private CandidateService service;

    @BeforeEach
    void setUp() {
        service = new CandidateService(candidateRepository, skillRepository, new CandidateMapper());
    }

    @Test
    void createNormalizesEmailAndSkillsReusesExistingSkillsAndCreatesMissingOnes() {
        when(candidateRepository.countByEmailIncludingDeleted("jane.doe@example.com")).thenReturn(0L);
        when(skillRepository.findByNameIn(Set.of("java", "spring"))).thenReturn(List.of(new Skill("java")));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.saveAndFlush(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CandidateResponse response = service.create(
                request("  Jane.Doe@Example.COM ", null, Set.of(" Spring", "JAVA", "java "), null));

        assertThat(response.email()).isEqualTo("jane.doe@example.com");
        assertThat(response.status()).isEqualTo(CandidateStatus.NEW);
        assertThat(response.skills()).containsExactly("java", "spring");
        verify(skillRepository).save(new Skill("spring"));
        verify(skillRepository, never()).save(new Skill("java"));
    }

    @Test
    void createRejectsEmailThatIsAlreadyUsedIncludingSoftDeletedCandidates() {
        when(candidateRepository.countByEmailIncludingDeleted("taken@example.com")).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request("Taken@example.com", null, null, null)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void getByIdThrowsWhenCandidateIsMissing() {
        when(candidateRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L)).isInstanceOf(CandidateNotFoundException.class);
    }

    @Test
    void searchMapsCandidatesAndPreservesPageMetadata() {
        Candidate candidate = existingCandidate(7L, 3L);
        candidate.replaceSkills(Set.of(new Skill("spring"), new Skill("java")));
        CandidateSearchCriteria criteria = new CandidateSearchCriteria(
                null, null, null, null, null, null, null, null, 1, 2, "lastName,asc");
        when(candidateRepository.findAll(ArgumentMatchers.<Specification<Candidate>>any(),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(candidate), criteria.toPageable(), 3));

        PageResponse<CandidateResponse> response = service.search(criteria);

        assertThat(response.content()).extracting(CandidateResponse::id).containsExactly(7L);
        assertThat(response.content().getFirst().skills()).containsExactly("java", "spring");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        verify(candidateRepository).findAll(ArgumentMatchers.<Specification<Candidate>>any(),
                eq(criteria.toPageable()));
    }

    @Test
    void updateAppliesFieldsKeepsStatusWhenNullAndReplacesSkills() {
        Candidate existing = existingCandidate(7L, 3L);
        existing.setStatus(CandidateStatus.INTERVIEWING);
        existing.addSkill(new Skill("cobol"));
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(candidateRepository.countByEmailIncludingDeletedExcludingId("new@example.com", 7L)).thenReturn(0L);
        when(skillRepository.findByNameIn(anyCollection())).thenReturn(List.of(new Skill("java")));
        when(candidateRepository.saveAndFlush(existing)).thenReturn(existing);

        CandidateResponse response = service.update(7L, request("NEW@example.com", null, Set.of("Java"), 3L));

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.currentTitle()).isEqualTo("Engineer");
        assertThat(response.status()).isEqualTo(CandidateStatus.INTERVIEWING);
        assertThat(response.skills()).containsExactly("java");
    }

    @Test
    void updateWithNullSkillsClearsExistingSkills() {
        Candidate existing = existingCandidate(7L, 3L);
        existing.addSkill(new Skill("java"));
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(candidateRepository.saveAndFlush(existing)).thenReturn(existing);

        CandidateResponse response = service.update(7L, request("old@example.com", null, null, 3L));

        assertThat(existing.getSkills()).isEmpty();
        assertThat(response.skills()).isEmpty();
    }

    @Test
    void updateChecksVersionBeforeEmailUniqueness() {
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existingCandidate(7L, 3L)));

        assertThatThrownBy(() -> service.update(7L, request("other@example.com", null, null, 2L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(candidateRepository, never()).countByEmailIncludingDeletedExcludingId(anyString(), anyLong());
    }

    @Test
    void updateWithStaleVersionThrowsOptimisticLockingFailure() {
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existingCandidate(7L, 3L)));

        assertThatThrownBy(() -> service.update(7L, request("a@example.com", null, null, 2L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsEmailUsedByAnotherCandidate() {
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existingCandidate(7L, 0L)));
        when(candidateRepository.countByEmailIncludingDeletedExcludingId("other@example.com", 7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(7L, request("other@example.com", null, null, 0L)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(candidateRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteMarksCandidateDeletedAndSavesIt() {
        Candidate existing = existingCandidate(7L, 0L);
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(7L);

        assertThat(existing.isDeleted()).isTrue();
        verify(candidateRepository).save(existing);
        verify(candidateRepository, never()).delete(any(Candidate.class));
    }

    @Test
    void deleteThrowsWhenCandidateIsMissing() {
        when(candidateRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(CandidateNotFoundException.class);
    }

    private static Candidate existingCandidate(Long id, Long version) {
        Candidate candidate = new Candidate("Old", "Name", "old@example.com");
        ReflectionTestUtils.setField(candidate, "id", id);
        ReflectionTestUtils.setField(candidate, "version", version);
        return candidate;
    }

    private static CandidateRequest request(String email, CandidateStatus status, Set<String> skills, Long version) {
        return new CandidateRequest("Jane", "Doe", email, "+91 1234567890", "Engineer", "Acme", "Pune",
                new BigDecimal("5.5"), 30, status, "notes", skills, version);
    }
}
