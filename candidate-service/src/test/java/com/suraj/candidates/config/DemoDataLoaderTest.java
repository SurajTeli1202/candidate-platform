package com.suraj.candidates.config;

import com.suraj.candidates.candidate.Candidate;
import com.suraj.candidates.candidate.CandidateRepository;
import com.suraj.candidates.candidate.CandidateService;
import com.suraj.candidates.candidate.CandidateStatus;
import com.suraj.candidates.skill.Skill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:candidates-demo;DB_CLOSE_DELAY=-1")
@EmbeddedKafka(
        partitions = 3,
        topics = "candidate-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@ActiveProfiles("demo")
class DemoDataLoaderTest {

    private final DemoDataLoader loader;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;
    private final int port;

    @Autowired
    DemoDataLoaderTest(DemoDataLoader loader, CandidateRepository candidateRepository,
                       CandidateService candidateService, @LocalServerPort int port) {
        this.loader = loader;
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
        this.port = port;
    }

    @Test
    @Transactional
    void loadsFictionalCandidatesOnStartupAndSkipsSubsequentRuns() throws Exception {
        List<Candidate> candidates = candidateRepository.findAll();
        assertThat(candidates).hasSize(30);
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.getLastName()).isEqualTo("Demo");
            assertThat(candidate.getEmail()).startsWith("demo.").endsWith("@example.com").isLowerCase();
            assertThat(candidate.getNotes()).contains("Fictional");
        });
        assertThat(candidates).extracting(Candidate::getStatus)
                .contains(CandidateStatus.values());
        assertThat(candidates).extracting(Candidate::getLocation)
                .contains("Pune", "Bangalore", "Mumbai", "Remote");
        assertThat(candidates).extracting(Candidate::getExperienceYears)
                .contains(new BigDecimal("0.5"), new BigDecimal("15.0"), null);
        assertThat(candidates).extracting(Candidate::getNoticePeriodDays).contains(0, 90);
        assertThat(candidates).anySatisfy(candidate -> assertThat(candidate.getSkills()).isEmpty());
        assertThat(candidates).extracting(Candidate::getCurrentCompany).containsNull();
        assertThat(candidates.stream().flatMap(candidate -> candidate.getSkills().stream()).map(Skill::getName))
                .contains("java", "spring", "react", "aws", "docker", "sql", "kafka", "kubernetes", "python")
                .allSatisfy(name -> assertThat(name).isLowerCase().isEqualTo(name.trim()));

        List<Long> ids = candidates.stream().map(Candidate::getId).toList();
        loader.run(new DefaultApplicationArguments(new String[0]));
        assertThat(candidateRepository.findAll()).extracting(Candidate::getId)
                .containsExactlyInAnyOrderElementsOf(ids);
        assertThat(candidateRepository.countIncludingDeleted()).isEqualTo(30);

        for (Long id : ids) {
            candidateService.delete(id);
        }
        assertThat(candidateRepository.count()).isZero();
        loader.run(new DefaultApplicationArguments(new String[0]));
        assertThat(candidateRepository.count()).isZero();
        assertThat(candidateRepository.countIncludingDeleted()).isEqualTo(30);

        HttpResponse<String> console = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/h2-console/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(console.statusCode()).isEqualTo(200);
        assertThat(console.body()).contains("H2 Console");
    }
}
