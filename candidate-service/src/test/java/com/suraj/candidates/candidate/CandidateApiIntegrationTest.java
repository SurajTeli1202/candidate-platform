package com.suraj.candidates.candidate;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack (controller, service, JPA, Flyway) against a private in-memory H2 database. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:candidates-e2e;DB_CLOSE_DELAY=-1")
@EmbeddedKafka(
        partitions = 3,
        topics = "candidate-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CandidateApiIntegrationTest {

    private final MockMvc mockMvc;

    @Autowired
    CandidateApiIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @CsvSource({"107374183,20", "5001,20", "100001,1"})
    void searchRejectsOffsetsAbove100000WithAdviceToNarrowTheSearch(int page, int size) throws Exception {
        mockMvc.perform(get("/api/v1/candidates")
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("pageOffsetValid"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("narrow the search")));
    }

    @ParameterizedTest
    @CsvSource({"5000,20", "1000,100", "100000,1"})
    void searchAcceptsAnOffsetOfExactly100000ThroughTheRealRepository(int page, int size) throws Exception {
        String lastName = "Offset" + size;
        createSearchCandidate(lastName, "[]");

        mockMvc.perform(get("/api/v1/candidates")
                        .param("q", "search-" + lastName + "@example.test")
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void invalidCandidateIdLogsTheMatchedTemplateWithoutTheEmail(CapturedOutput output) throws Exception {
        String email = "person@example.com";

        mockMvc.perform(get("/api/v1/candidates/" + email))
                .andExpect(status().isBadRequest());

        assertThat(output.getAll())
                .contains("HTTP GET /api/v1/candidates/{id} status=400")
                .doesNotContain(email);
    }

    @Test
    void searchFiltersSkillsAndReturnsStablePagesWithoutDeletedCandidates() throws Exception {
        createSearchCandidate("Alpha", "[\"Java\", \"Spring\"]");
        createSearchCandidate("Beta", "[\"Java\"]");
        createSearchCandidate("Gamma", "[\"Kotlin\"]");
        String deletedLocation = createSearchCandidate("Deleted", "[\"Java\", \"Spring\"]");
        mockMvc.perform(delete(deletedLocation)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/candidates")
                        .param("q", "searchfixture engineer")
                        .param("status", "NEW,SCREENING")
                        .param("location", "PUNE")
                        .param("skills", " JAVA , Spring ")
                        .param("minExperience", "4.5")
                        .param("maxExperience", "4.5")
                        .param("maxNoticePeriod", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].lastName").value("Alpha"))
                .andExpect(jsonPath("$.content[0].skills").value(contains("java", "spring")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/v1/candidates")
                        .param("q", "searchfixture")
                        .param("skills", "java,spring")
                        .param("skillMatch", "ANY")
                        .param("size", "1")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].lastName").value("Alpha"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/candidates")
                        .param("q", "searchfixture")
                        .param("skills", "java,spring")
                        .param("skillMatch", "ANY")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].lastName").value("Beta"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void createGetUpdateDeleteLifecycle() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "email": "  Jane.E2E@Example.com ",
                                  "experienceYears": 4.5,
                                  "skills": ["Spring", " java ", "JAVA"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane.e2e@example.com"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.skills").value(contains("java", "spring")))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String location = created.getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.skills").value(contains("java", "spring")));

        String update = """
                {
                  "version": %d,
                  "firstName": "Jane",
                  "lastName": "Doe",
                  "email": "jane.e2e@example.com",
                  "currentTitle": "Senior Engineer",
                  "status": "SCREENING",
                  "skills": ["Java", "Kotlin"]
                }
                """;
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(update.formatted(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTitle").value("Senior Engineer"))
                .andExpect(jsonPath("$.status").value("SCREENING"))
                .andExpect(jsonPath("$.skills").value(contains("java", "kotlin")))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(update.formatted(0)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(location))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(location))
                .andExpect(status().isNotFound());

        // The soft-deleted candidate still reserves its email.
        mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Other", "lastName": "Person", "email": "JANE.E2E@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate email"));

        Integer id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(delete("/api/v1/candidates/" + id))
                .andExpect(status().isNotFound());
    }

    private String createSearchCandidate(String lastName, String skillsJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "SearchFixture",
                                  "lastName": "%s",
                                  "email": "search-%s@example.test",
                                  "currentTitle": "Engineer",
                                  "location": "Pune",
                                  "status": "SCREENING",
                                  "experienceYears": 4.5,
                                  "noticePeriodDays": 30,
                                  "skills": %s
                                }
                                """.formatted(lastName, lastName, skillsJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }
}
