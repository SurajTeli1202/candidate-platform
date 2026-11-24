package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateSearchCriteria;
import com.suraj.candidates.config.JpaAuditingConfig;
import com.suraj.candidates.skill.Skill;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaAuditingConfig.class)
class CandidateSpecificationsTest {

    private final CandidateRepository repository;
    private final EntityManager entityManager;
    private Candidate alice;
    private Candidate bob;
    private Candidate carol;
    private Candidate dave;

    @Autowired
    CandidateSpecificationsTest(CandidateRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        Skill java = new Skill("java");
        Skill spring = new Skill("spring");
        Skill kotlin = new Skill("kotlin");
        entityManager.persist(java);
        entityManager.persist(spring);
        entityManager.persist(kotlin);
        alice = candidate("Alice", "Adams", "alice@example.test", "Senior Backend Engineer",
                "Acme Labs", "Pune", "5.0", 30, CandidateStatus.SCREENING, Set.of(java, spring, kotlin));
        bob = candidate("Bob", "Brown", "bob@example.test", "Data Engineer",
                "DataWorks", "PUNE", "2.0", 60, CandidateStatus.NEW, Set.of(java));
        carol = candidate("Carol", "Clark", "carol@example.test", "Architect",
                "Acme Labs", "Mumbai", "8.0", 0, CandidateStatus.INTERVIEWING, Set.of(spring));
        dave = candidate("Dave", "Davis", "dave@example.test", "Developer",
                "Other", "Pune", null, null, CandidateStatus.HIRED, Set.of());
        Candidate deleted = candidate("Deleted", "Ghost", "ghost@example.test", "Senior Backend Engineer",
                "Acme Labs", "Pune", "5.0", 30, CandidateStatus.SCREENING, Set.of(java, spring));
        deleted.markDeleted();
        repository.saveAllAndFlush(List.of(alice, bob, carol, dave, deleted));
        entityManager.clear();
    }

    @Test
    void noFiltersReturnsOnlyActiveCandidates() {
        assertThat(search(emptyCriteria()).getContent()).extracting(Candidate::getId)
                .containsExactlyInAnyOrder(alice.getId(), bob.getId(), carol.getId(), dave.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"aLiCe", "ADAMS", "alice@EXAMPLE", "senior BACKEND", "acme LABS"})
    void textSearchMatchesEachSearchableFieldIgnoringCase(String q) {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                q, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId).contains(alice.getId());
        assertThat(result.getContent()).extracting(Candidate::getId).doesNotContain(bob.getId(), dave.getId());
    }

    @Test
    void everyTextTokenMustMatchButTokensMayMatchDifferentFields() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                "  ALICE \t adams\nBACKEND acme  ", null, null, null, null,
                null, null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId).containsExactly(alice.getId());
    }

    @Test
    void textSearchRejectsCandidateWhenOneTokenDoesNotMatch() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                "Alice missing", null, null, null, null, null, null, null, null, null, null));

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "!%_"})
    void textWildcardAndEscapeCharactersAreTreatedLiterally(String literal) {
        Candidate matching = candidate("Literal" + literal + "Value", "Match", "literal@example.test",
                null, null, null, null, null, CandidateStatus.NEW, Set.of());
        Candidate nonMatching = candidate("LiteralXValue", "Other", "other@example.test",
                null, null, null, null, null, CandidateStatus.NEW, Set.of());
        repository.saveAllAndFlush(List.of(matching, nonMatching));
        entityManager.clear();

        Page<Candidate> result = search(new CandidateSearchCriteria(
                literal, null, null, null, null, null, null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId).containsExactly(matching.getId());
    }

    @Test
    void statusMatchesAnyRequestedStatus() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, Set.of(CandidateStatus.NEW, CandidateStatus.INTERVIEWING), null, null, null,
                null, null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId)
                .containsExactlyInAnyOrder(bob.getId(), carol.getId());
    }

    @Test
    void locationIsCaseInsensitiveExactMatch() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, "  pUnE ", null, null, null, null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId)
                .containsExactlyInAnyOrder(alice.getId(), bob.getId(), dave.getId());
        assertThat(search(new CandidateSearchCriteria(
                null, null, "Pun", null, null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void minimumExperienceIsInclusiveAndExcludesUnknownExperience() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, null, null, new BigDecimal("5.0"), null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId)
                .containsExactlyInAnyOrder(alice.getId(), carol.getId());
    }

    @Test
    void maximumExperienceIsInclusiveAndExcludesUnknownExperience() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, null, null, null, new BigDecimal("5.0"), null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId)
                .containsExactlyInAnyOrder(alice.getId(), bob.getId());
    }

    @Test
    void maximumNoticePeriodIsInclusiveAndExcludesUnknownNotice() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, null, null, null, null, 30, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId)
                .containsExactlyInAnyOrder(alice.getId(), carol.getId());
    }

    @Test
    void allSkillsIsTheDefaultAndNormalizesAndDeduplicatesNames() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, Set.of(" JAVA ", "java", "SPRING"), null,
                null, null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId).containsExactly(alice.getId());
    }

    @Test
    void anySkillMatchesWithoutDuplicatingCandidatesOrCounts() {
        CandidateSearchCriteria criteria = new CandidateSearchCriteria(
                null, null, null, Set.of("java", "spring"), SkillMatch.ANY,
                null, null, null, 0, 1, "firstName,asc");
        Page<Candidate> first = search(criteria);
        Page<Candidate> second = repository.findAll(CandidateSpecifications.from(criteria),
                criteria.toPageable().next());
        Page<Candidate> third = repository.findAll(CandidateSpecifications.from(criteria),
                criteria.toPageable().next().next());

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).extracting(Candidate::getId).containsExactly(alice.getId());
        assertThat(second.getContent()).extracting(Candidate::getId).containsExactly(bob.getId());
        assertThat(third.getContent()).extracting(Candidate::getId).containsExactly(carol.getId());
    }

    @Test
    void allSkillsWithAnUnknownSkillMatchesNoCandidates() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, Set.of("java", "unknown"), SkillMatch.ALL,
                null, null, null, null, null, null));

        assertThat(result).isEmpty();
    }

    @Test
    void allSkillsSubqueryKeepsPageCountsAccurate() {
        Candidate secondMatch = new Candidate("Second", "Match", "second-match@example.test");
        secondMatch.replaceSkills(Set.copyOf(entityManager.createQuery("SELECT s FROM Skill s", Skill.class)
                .getResultList()));
        repository.saveAndFlush(secondMatch);
        entityManager.clear();
        CandidateSearchCriteria criteria = new CandidateSearchCriteria(
                null, null, null, Set.of("java", "spring"), SkillMatch.ALL,
                null, null, null, 0, 1, "firstName,asc");

        Page<Candidate> first = search(criteria);
        Page<Candidate> second = repository.findAll(CandidateSpecifications.from(criteria),
                criteria.toPageable().next());

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(Candidate::getId).containsExactly(alice.getId());
        assertThat(second.getContent()).extracting(Candidate::getId).containsExactly(secondMatch.getId());
    }

    @Test
    void anySkillCanMatchWhenAnotherRequestedSkillIsUnknown() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, Set.of("java", "unknown"), SkillMatch.ANY,
                null, null, null, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId)
                .containsExactlyInAnyOrder(alice.getId(), bob.getId());
    }

    @Test
    void combinesAllPresentFiltersWithAnd() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                "engineer acme", Set.of(CandidateStatus.SCREENING), "pune",
                Set.of("java", "spring"), SkillMatch.ALL, new BigDecimal("5.0"),
                new BigDecimal("5.0"), 30, null, null, null));

        assertThat(result.getContent()).extracting(Candidate::getId).containsExactly(alice.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void blankTextAndLocationDoNotFilter() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                " \t ", null, "  ", null, null, null, null, null, null, null, null));

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    void lastNameDescendingSortAndPagingHaveCorrectTotals() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, null, null, null, null, null, 1, 2, "lastName,desc"));

        assertThat(result.getContent()).extracting(Candidate::getId).containsExactly(bob.getId(), alice.getId());
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void equalSortValuesUseIdAscendingForStablePages() {
        entityManager.createNativeQuery("UPDATE candidate SET first_name = 'Same'").executeUpdate();
        entityManager.clear();
        CandidateSearchCriteria criteria = new CandidateSearchCriteria(
                null, null, null, null, null, null, null, null, 0, 2, "firstName,desc");

        Page<Candidate> first = search(criteria);
        Page<Candidate> second = repository.findAll(CandidateSpecifications.from(criteria),
                criteria.toPageable().next());

        assertThat(first.getContent()).extracting(Candidate::getId).containsExactly(alice.getId(), bob.getId());
        assertThat(second.getContent()).extracting(Candidate::getId).containsExactly(carol.getId(), dave.getId());
        assertThat(search(criteria).getContent()).extracting(Candidate::getId)
                .containsExactly(alice.getId(), bob.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void experienceSortingPlacesNullsLastAcrossPages(String direction) {
        CandidateSearchCriteria criteria = new CandidateSearchCriteria(
                null, null, null, null, null, null, null, null, 0, 2,
                "experienceYears," + direction);

        Page<Candidate> first = search(criteria);
        Page<Candidate> second = repository.findAll(CandidateSpecifications.from(criteria),
                criteria.toPageable().next());

        List<Long> expected = direction.equals("asc")
                ? List.of(bob.getId(), alice.getId(), carol.getId(), dave.getId())
                : List.of(carol.getId(), alice.getId(), bob.getId(), dave.getId());
        assertThat(first.getContent()).extracting(Candidate::getId)
                .containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(second.getContent()).extracting(Candidate::getId)
                .containsExactlyElementsOf(expected.subList(2, 4));
        assertThat(second.getContent().getLast().getExperienceYears()).isNull();
        assertThat(first.getTotalElements()).isEqualTo(4);
    }

    @Test
    void defaultSortUsesNewestCreatedAtFirstAndIdAscendingForTies() {
        entityManager.createNativeQuery("UPDATE candidate SET created_at = :createdAt")
                .setParameter("createdAt", Instant.parse("2026-01-01T00:00:00Z"))
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE candidate SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", Instant.parse("2026-02-01T00:00:00Z"))
                .setParameter("id", carol.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(search(emptyCriteria()).getContent()).extracting(Candidate::getId)
                .containsExactly(carol.getId(), alice.getId(), bob.getId(), dave.getId());
    }

    @Test
    void pageBeyondTheResultsIsEmptyButKeepsTheTotal() {
        Page<Candidate> result = search(new CandidateSearchCriteria(
                null, null, null, null, null, null, null, null, 9, 2, "firstName,asc"));

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    private Page<Candidate> search(CandidateSearchCriteria criteria) {
        return repository.findAll(CandidateSpecifications.from(criteria), criteria.toPageable());
    }

    private static CandidateSearchCriteria emptyCriteria() {
        return new CandidateSearchCriteria(null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Candidate candidate(String firstName, String lastName, String email, String title,
                                       String company, String location, String experience, Integer notice,
                                       CandidateStatus status, Set<Skill> skills) {
        Candidate candidate = new Candidate(firstName, lastName, email);
        candidate.setCurrentTitle(title);
        candidate.setCurrentCompany(company);
        candidate.setLocation(location);
        candidate.setExperienceYears(experience == null ? null : new BigDecimal(experience));
        candidate.setNoticePeriodDays(notice);
        candidate.setStatus(status);
        candidate.replaceSkills(skills);
        return candidate;
    }
}
