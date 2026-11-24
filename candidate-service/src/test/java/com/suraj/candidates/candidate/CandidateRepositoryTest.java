package com.suraj.candidates.candidate;

import com.suraj.candidates.config.JpaAuditingConfig;
import com.suraj.candidates.skill.Skill;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against an in-memory H2 whose schema is created by Flyway (V1__init.sql).
 * ddl-auto=validate makes Hibernate fail fast if the entities don't match the migration.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaAuditingConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class CandidateRepositoryTest {

    private final CandidateRepository candidateRepository;
    private final EntityManager entityManager;
    private final Flyway flyway;

    @Autowired
    CandidateRepositoryTest(CandidateRepository candidateRepository, EntityManager entityManager, Flyway flyway) {
        this.candidateRepository = candidateRepository;
        this.entityManager = entityManager;
        this.flyway = flyway;
    }

    @Test
    void schemaIsCreatedByFlyway() {
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("1");
    }

    @Test
    void savesCandidateWithSkillsAndReadsItBack() {
        Skill java = persistSkill("  Java ");
        Skill spring = persistSkill("SPRING");
        Candidate candidate = newCandidate("jane.doe@example.com");
        candidate.setExperienceYears(new BigDecimal("5.5"));
        candidate.setNoticePeriodDays(30);
        candidate.addSkill(java);
        candidate.addSkill(spring);

        Long id = candidateRepository.saveAndFlush(candidate).getId();
        entityManager.clear();

        Candidate found = candidateRepository.findById(id).orElseThrow();
        assertThat(found.getFirstName()).isEqualTo("Jane");
        assertThat(found.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(found.getStatus()).isEqualTo(CandidateStatus.NEW);
        assertThat(found.getExperienceYears()).isEqualByComparingTo("5.5");
        assertThat(found.getNoticePeriodDays()).isEqualTo(30);
        assertThat(found.getVersion()).isZero();
        assertThat(found.isDeleted()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getSkills()).extracting(Skill::getName).containsExactlyInAnyOrder("java", "spring");
    }

    @Test
    void persistsStatusAsNameAndReadsItBack() {
        Candidate candidate = newCandidate("status@example.com");
        candidate.setStatus(CandidateStatus.INTERVIEWING);
        Long id = candidateRepository.saveAndFlush(candidate).getId();
        entityManager.clear();

        Object stored = entityManager
                .createNativeQuery("SELECT status FROM candidate WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(stored).isEqualTo("INTERVIEWING");
        assertThat(candidateRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(CandidateStatus.INTERVIEWING);
    }

    @Test
    void rejectsDuplicateEmailWithoutLoggingItsValue(CapturedOutput output) {
        String email = "duplicate-log-check@example.com";
        candidateRepository.saveAndFlush(newCandidate(email));

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(newCandidate(email)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("UQ_CANDIDATE_EMAIL");
        assertThat(output.getAll()).doesNotContain(email);
    }

    @Test
    void rejectsNegativeExperienceYears() {
        Candidate candidate = newCandidate("negative-experience@example.com");
        candidate.setExperienceYears(new BigDecimal("-0.1"));

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(candidate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("CK_CANDIDATE_EXPERIENCE_YEARS");
    }

    @Test
    void rejectsNegativeNoticePeriodDays() {
        Candidate candidate = newCandidate("negative-notice@example.com");
        candidate.setNoticePeriodDays(-1);

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(candidate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("CK_CANDIDATE_NOTICE_PERIOD_DAYS");
    }

    @Test
    void markDeletedSoftDeletesKeepsSkillsAndHidesTheRow() {
        Candidate candidate = newCandidate("soft@example.com");
        candidate.addSkill(persistSkill("kotlin"));
        Long id = candidateRepository.saveAndFlush(candidate).getId();
        Instant oldUpdatedAt = Instant.parse("2020-01-01T00:00:00Z");
        entityManager.createNativeQuery("UPDATE candidate SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", oldUpdatedAt)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();

        Candidate loaded = candidateRepository.findById(id).orElseThrow();
        loaded.markDeleted();
        candidateRepository.saveAndFlush(loaded);
        entityManager.clear();

        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT deleted, version, updated_at FROM candidate WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(row[0]).isEqualTo(Boolean.TRUE);
        assertThat(((Number) row[1]).longValue()).isEqualTo(1L);
        assertThat(toInstant(row[2])).isAfter(oldUpdatedAt);
        assertThat(countSkillLinks(id)).isEqualTo(1);
        assertThat(candidateRepository.findById(id)).isEmpty();
        assertThat(candidateRepository.findAll()).isEmpty();
    }

    @Test
    void emailOfSoftDeletedCandidateStaysReserved() {
        Candidate candidate = candidateRepository.saveAndFlush(newCandidate("reserved@example.com"));
        candidate.markDeleted();
        candidateRepository.saveAndFlush(candidate);

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(newCandidate("reserved@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateWithStaleVersionFailsWithOptimisticLock() {
        Candidate candidate = candidateRepository.saveAndFlush(newCandidate("stale@example.com"));
        // Simulate a concurrent update that the managed entity doesn't know about.
        entityManager.createNativeQuery("UPDATE candidate SET version = version + 1 WHERE id = :id")
                .setParameter("id", candidate.getId())
                .executeUpdate();

        candidate.markDeleted();

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(candidate))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private int countSkillLinks(Long candidateId) {
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM candidate_skill WHERE candidate_id = :id")
                .setParameter("id", candidateId)
                .getSingleResult();
        return count.intValue();
    }

    private static Instant toInstant(Object timestamp) {
        return switch (timestamp) {
            case Instant instant -> instant;
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            default -> throw new IllegalStateException("Unexpected timestamp type: " + timestamp.getClass());
        };
    }

    private Skill persistSkill(String name) {
        Skill skill = new Skill(name);
        entityManager.persist(skill);
        return skill;
    }

    private static Candidate newCandidate(String email) {
        return new Candidate("Jane", "Doe", email);
    }
}
