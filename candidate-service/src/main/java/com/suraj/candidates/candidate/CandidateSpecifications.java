package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateSearchCriteria;
import com.suraj.candidates.skill.Skill;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class CandidateSpecifications {

    private static final char LIKE_ESCAPE = '!';
    private static final List<String> TEXT_FIELDS =
            List.of("firstName", "lastName", "email", "currentTitle", "currentCompany");

    private CandidateSpecifications() {
    }

    public static Specification<Candidate> from(CandidateSearchCriteria criteria) {
        List<Specification<Candidate>> filters = new ArrayList<>();
        if (StringUtils.hasText(criteria.q())) {
            filters.add(textContainsAllTokens(criteria.q()));
        }
        if (!criteria.status().isEmpty()) {
            filters.add(hasStatus(criteria.status()));
        }
        if (StringUtils.hasText(criteria.location())) {
            filters.add(locatedIn(criteria.location()));
        }
        if (!criteria.skills().isEmpty()) {
            filters.add(hasSkills(criteria.skills(), criteria.skillMatch()));
        }
        if (criteria.minExperience() != null) {
            filters.add(experienceAtLeast(criteria.minExperience()));
        }
        if (criteria.maxExperience() != null) {
            filters.add(experienceAtMost(criteria.maxExperience()));
        }
        if (criteria.maxNoticePeriod() != null) {
            filters.add(noticePeriodAtMost(criteria.maxNoticePeriod()));
        }
        return Specification.allOf(filters);
    }

    public static Specification<Candidate> textContainsAllTokens(String text) {
        String[] tokens = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return (root, query, cb) -> cb.and(Arrays.stream(tokens)
                .map(token -> {
                    String pattern = "%" + escapeLike(token) + "%";
                    return cb.or(TEXT_FIELDS.stream()
                            .map(field -> cb.like(cb.lower(root.get(field)), pattern, LIKE_ESCAPE))
                            .toArray(Predicate[]::new));
                })
                .toArray(Predicate[]::new));
    }

    public static Specification<Candidate> hasStatus(Set<CandidateStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Candidate> locatedIn(String location) {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("location")), normalized);
    }

    public static Specification<Candidate> hasSkills(Set<String> names, SkillMatch match) {
        Set<String> normalized = names.stream().map(Skill::normalize).collect(Collectors.toSet());
        return (root, query, cb) -> {
            // Correlate inside the subquery: the paged outer query never joins the skill collection.
            Subquery<Long> matchingSkills = query.subquery(Long.class);
            SetJoin<Candidate, Skill> skill = matchingSkills.correlate(root).joinSet("skills");
            matchingSkills.where(skill.get("name").in(normalized));
            if (match == SkillMatch.ALL) {
                matchingSkills.select(cb.countDistinct(skill.get("id")));
                return cb.equal(matchingSkills, (long) normalized.size());
            }
            matchingSkills.select(skill.get("id"));
            return cb.exists(matchingSkills);
        };
    }

    public static Specification<Candidate> experienceAtLeast(BigDecimal minimum) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("experienceYears"), minimum);
    }

    public static Specification<Candidate> experienceAtMost(BigDecimal maximum) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("experienceYears"), maximum);
    }

    public static Specification<Candidate> noticePeriodAtMost(Integer maximum) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("noticePeriodDays"), maximum);
    }

    private static String escapeLike(String token) {
        return token.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
