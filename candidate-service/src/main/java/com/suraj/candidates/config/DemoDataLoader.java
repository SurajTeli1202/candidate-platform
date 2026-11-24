package com.suraj.candidates.config;

import com.suraj.candidates.candidate.CandidateRepository;
import com.suraj.candidates.candidate.CandidateService;
import com.suraj.candidates.candidate.CandidateStatus;
import com.suraj.candidates.candidate.dto.CandidateRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final List<String> NAMES = List.of(
            "Aster", "Bramble", "Cobalt", "Dapple", "Ember", "Fable",
            "Glimmer", "Hearth", "Indigo", "Juniper", "Kestrel", "Lumen",
            "Meadow", "Nimbus", "Opal", "Pebble", "Quill", "Ripple",
            "Solstice", "Thistle", "Umber", "Velvet", "Willow", "Xylo",
            "Yarrow", "Zephyr", "Acorn", "Beryl", "Clover", "Dusk");
    private static final List<String> LOCATIONS = List.of("Pune", "Bangalore", "Mumbai", "Remote");
    private static final List<String> TITLES = List.of(
            "Backend Developer", "Frontend Developer", "Cloud Engineer",
            "Data Engineer", "Platform Engineer", "Graduate Developer");
    private static final List<Integer> NOTICE_PERIODS = List.of(0, 15, 30, 45, 60, 90);
    private static final List<Set<String>> SKILLS = List.of(
            Set.of(" Java ", "SPRING", "sql"),
            Set.of("react", "java"),
            Set.of("aws", "docker", "kubernetes"),
            Set.of("python", "sql", "kafka"),
            Set.of("java", "kafka", "docker"),
            Set.of());

    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;
    private final Validator validator;

    public DemoDataLoader(CandidateRepository candidateRepository, CandidateService candidateService,
                          Validator validator) {
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
        this.validator = validator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (candidateRepository.countIncludingDeleted() > 0) {
            log.info("Skipping demo data: candidate rows already exist");
            return;
        }
        for (int index = 0; index < NAMES.size(); index++) {
            CandidateRequest request = candidateRequest(index);
            var violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            candidateService.create(request);
        }
        log.info("Loaded {} fictional demo candidates", NAMES.size());
    }

    private CandidateRequest candidateRequest(int index) {
        String name = NAMES.get(index);
        return new CandidateRequest(
                name, "Demo", "demo." + name + "@example.com", null,
                index % 10 == 8 ? null : TITLES.get(index % TITLES.size()),
                index % 5 == 0 ? null : "Fictional Cloudworks",
                LOCATIONS.get(index % LOCATIONS.size()),
                index % 10 == 8 ? null : BigDecimal.valueOf((index + 1) * 5L, 1),
                NOTICE_PERIODS.get(index % NOTICE_PERIODS.size()),
                CandidateStatus.values()[index % CandidateStatus.values().length],
                "Fictional demo candidate; not a real person.",
                SKILLS.get(index % SKILLS.size()), null);
    }
}
