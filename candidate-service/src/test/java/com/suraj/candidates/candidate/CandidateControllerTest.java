package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateRequest;
import com.suraj.candidates.candidate.dto.CandidateResponse;
import com.suraj.candidates.candidate.dto.CandidateSearchCriteria;
import com.suraj.candidates.candidate.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Also covers GlobalExceptionHandler, which @WebMvcTest picks up as a @RestControllerAdvice. */
@WebMvcTest(CandidateController.class)
class CandidateControllerTest {

    private static final String BASE = "/api/v1/candidates";

    private static final String VALID_BODY = """
            {
              "firstName": "Jane",
              "lastName": "Doe",
              "email": "jane@example.com",
              "experienceYears": 5.5,
              "noticePeriodDays": 30,
              "skills": ["Java", "Spring"]
            }
            """;

    private final MockMvc mockMvc;

    @MockitoBean
    private CandidateService candidateService;

    @Autowired
    CandidateControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void mvcSliceAutomaticallyIncludesRequestIdFilter() throws Exception {
        when(candidateService.getById(1L)).thenReturn(response(1L, 0L));

        String requestId = "d3864f78-5b32-4bd0-9f98-3f1b11260bc3";
        mockMvc.perform(get(BASE + "/1").header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId));

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void searchReturnsOnlyTheDocumentedPageShapeAndUsesDefaults() throws Exception {
        when(candidateService.search(any())).thenReturn(
                new PageResponse<>(List.of(response(1L, 0L)), 0, 20, 1, 1));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].skills[0]").value("java"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist());

        ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
        verify(candidateService).search(captor.capture());
        CandidateSearchCriteria criteria = captor.getValue();
        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(20);
        assertThat(criteria.skillMatch()).isEqualTo(SkillMatch.ALL);
        assertThat(criteria.toPageable().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt").nullsLast(), Sort.Order.asc("id").nullsLast()));
    }

    @Test
    void searchBindsCommaSeparatedStatusesAndSkills() throws Exception {
        when(candidateService.search(any())).thenReturn(new PageResponse<>(List.of(), 2, 10, 0, 0));

        mockMvc.perform(get(BASE)
                        .param("q", "Jane engineer")
                        .param("status", "NEW,SCREENING")
                        .param("location", "Pune")
                        .param("skills", " Java ,Spring")
                        .param("skillMatch", "ANY")
                        .param("minExperience", "1.5")
                        .param("maxExperience", "5.0")
                        .param("maxNoticePeriod", "30")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
        verify(candidateService).search(captor.capture());
        CandidateSearchCriteria criteria = captor.getValue();
        assertThat(criteria.q()).isEqualTo("Jane engineer");
        assertThat(criteria.status()).containsExactlyInAnyOrder(CandidateStatus.NEW, CandidateStatus.SCREENING);
        assertThat(criteria.location()).isEqualTo("Pune");
        assertThat(new CandidateMapper().normalizeSkillNames(criteria.skills()))
                .containsExactlyInAnyOrder("java", "spring");
        assertThat(criteria.skillMatch()).isEqualTo(SkillMatch.ANY);
        assertThat(criteria.minExperience()).isEqualByComparingTo("1.5");
        assertThat(criteria.maxExperience()).isEqualByComparingTo("5.0");
        assertThat(criteria.maxNoticePeriod()).isEqualTo(30);
        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.size()).isEqualTo(10);
        assertThat(criteria.toPageable().getSort()).isEqualTo(Sort.by(
                Sort.Order.asc("lastName").nullsLast(), Sort.Order.asc("id").nullsLast()));
    }

    @Test
    void searchBindsRepeatedStatuses() throws Exception {
        when(candidateService.search(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BASE).param("status", "NEW", "HIRED"))
                .andExpect(status().isOk());

        ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
        verify(candidateService).search(captor.capture());
        assertThat(captor.getValue().status()).containsExactlyInAnyOrder(CandidateStatus.NEW, CandidateStatus.HIRED);
    }

    @ParameterizedTest
    @CsvSource({
            "page,-1", "size,0", "size,101",
            "minExperience,-0.1", "maxExperience,-0.1", "maxNoticePeriod,-1",
            "sort,email:asc", "sort,email", "sort,lastName:sideways",
            "sort,id:asc", "sort,skills.name:asc", "sort,lastName:asc:updatedAt"
    })
    void searchRejectsInvalidParameterValues(String field, String value) throws Exception {
        mockMvc.perform(get(BASE).param(field, value.replace(':', ',')))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems(field)))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(MethodArgumentNotValidException.class));
        verifyNoInteractions(candidateService);
    }

    @ParameterizedTest
    @CsvSource({
            "status,SLEEPING", "skillMatch,NONE", "page,abc", "size,2147483648",
            "minExperience,abc", "maxExperience,abc", "maxNoticePeriod,abc"
    })
    void searchBindingFailuresReturnSanitizedFieldErrors(String field, String value) throws Exception {
        mockMvc.perform(get(BASE).param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value for this parameter"))
                .andExpect(content().string(not(containsString(value))))
                .andExpect(jsonPath("$.instance").value(BASE))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(MethodArgumentNotValidException.class));
        verifyNoInteractions(candidateService);
    }

    @Test
    void searchRejectsBlankSkillNames() throws Exception {
        mockMvc.perform(get(BASE).param("skills", "java, ,spring"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("skills[]")));
        verifyNoInteractions(candidateService);
    }

    @Test
    void searchRejectsTextLongerThan100Characters() throws Exception {
        mockMvc.perform(get(BASE).param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("q")));
        verifyNoInteractions(candidateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"one two three four five six", "  one\ttwo\nthree\rfour\ffive six  "})
    void searchRejectsMoreThanFiveTextTokens(String q) throws Exception {
        mockMvc.perform(get(BASE).param("q", q))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("q")));
        verifyNoInteractions(candidateService);
    }

    @Test
    void searchRejectsMoreThan20Skills() throws Exception {
        String skills = IntStream.rangeClosed(1, 21).mapToObj(index -> "skill" + index)
                .collect(Collectors.joining(","));

        mockMvc.perform(get(BASE).param("skills", skills))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("skills")));
        verifyNoInteractions(candidateService);
    }

    @Test
    void searchRejectsSkillNamesLongerThan100Characters() throws Exception {
        mockMvc.perform(get(BASE).param("skills", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("skills[]")));
        verifyNoInteractions(candidateService);
    }

    @Test
    void searchRejectsLocationsLongerThan150Characters() throws Exception {
        mockMvc.perform(get(BASE).param("location", "a".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("location")));
        verifyNoInteractions(candidateService);
    }

    @Test
    void searchAcceptsInputAtTheNewLimits() throws Exception {
        when(candidateService.search(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        String skills = IntStream.range(10, 30)
                .mapToObj(index -> "a".repeat(98) + index)
                .collect(Collectors.joining(","));
        String q = "  " + "a".repeat(88) + "\tb\nc\rd\fe  ";

        assertThat(q).hasSize(100);
        mockMvc.perform(get(BASE)
                        .param("q", q)
                        .param("skills", skills)
                        .param("location", "a".repeat(150)))
                .andExpect(status().isOk());
        verify(candidateService).search(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW,", ",NEW", "NEW,,HIRED"})
    void searchRejectsEmptyEntriesWithinAStatusList(String statuses) throws Exception {
        mockMvc.perform(get(BASE).param("status", statuses))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("status[]")));
        verifyNoInteractions(candidateService);
    }

    @Test
    void searchAcceptsZeroBoundsAndMaximumPageSize() throws Exception {
        when(candidateService.search(any())).thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get(BASE)
                        .param("minExperience", "0")
                        .param("maxExperience", "0")
                        .param("maxNoticePeriod", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void searchRejectsInvertedExperienceRange() throws Exception {
        mockMvc.perform(get(BASE).param("minExperience", "5").param("maxExperience", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("experienceRangeValid"))
                .andExpect(jsonPath("$.errors[0].message").value("minExperience must not exceed maxExperience"));
        verifyNoInteractions(candidateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdAt", "updatedAt", "firstName", "lastName", "experienceYears", "status"})
    void searchAcceptsEachWhitelistedSortFieldAndAddsIdLast(String field) throws Exception {
        when(candidateService.search(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BASE).param("sort", field + ",desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
        verify(candidateService).search(captor.capture());
        assertThat(captor.getValue().toPageable().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc(field).nullsLast(), Sort.Order.asc("id").nullsLast()));
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(candidateService.create(any())).thenReturn(response(1L, 0L));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/candidates/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.skills[0]").value("java"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createWithInvalidFieldsReturns400WithFieldErrors() throws Exception {
        String body = """
                {
                  "firstName": " ",
                  "lastName": "%s",
                  "email": "not-an-email",
                  "experienceYears": -1.25,
                  "noticePeriodDays": -1,
                  "skills": ["%s"]
                }
                """.formatted("x".repeat(101), "s".repeat(51));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems(
                        "firstName", "lastName", "email", "experienceYears", "noticePeriodDays", "skills[]")))
                .andExpect(content().string(not(containsString("not-an-email"))));
        verifyNoInteractions(candidateService);
    }

    @Test
    void createWithMoreThan50SkillsReturns400() throws Exception {
        String skills = IntStream.rangeClosed(1, 51).mapToObj(i -> "\"skill" + i + "\"")
                .collect(Collectors.joining(","));
        String body = """
                {"firstName": "Jane", "lastName": "Doe", "email": "jane@example.com", "skills": [%s]}
                """.formatted(skills);

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skills"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"firstName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Malformed JSON request"));
    }

    @Test
    void unknownStatusReturns400ListingAllowedValues() throws Exception {
        String body = """
                {"firstName": "Jane", "lastName": "Doe", "email": "jane@example.com", "status": "SLEEPING"}
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("'status'")))
                .andExpect(jsonPath("$.detail").value(containsString("INTERVIEWING")));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(candidateService.create(any())).thenThrow(new DuplicateEmailException());

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate email"));
    }

    @Test
    void dataIntegrityViolationReturns409WithoutLeakingDetails() throws Exception {
        when(candidateService.create(any()))
                .thenThrow(new DataIntegrityViolationException("uq_candidate_email: jane@example.com"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data."));
    }

    @Test
    void getReturnsCandidate() throws Exception {
        when(candidateService.getById(1L)).thenReturn(response(1L, 2L));

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void getMissingCandidateReturns404() throws Exception {
        when(candidateService.getById(99L)).thenThrow(new CandidateNotFoundException(99L));

        mockMvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Candidate not found"))
                .andExpect(jsonPath("$.instance").value(BASE + "/99"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get(BASE + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void updateReturnsUpdatedCandidate() throws Exception {
        when(candidateService.update(eq(1L), any(CandidateRequest.class))).thenReturn(response(1L, 4L));

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(withVersion(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void updateWithoutVersionReturns400() throws Exception {
        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        verifyNoInteractions(candidateService);
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        when(candidateService.update(eq(1L), any(CandidateRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Candidate.class, 1L));

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(withVersion(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent());
        verify(candidateService).delete(1L);
    }

    @Test
    void deleteMissingCandidateReturns404() throws Exception {
        doThrow(new CandidateNotFoundException(5L)).when(candidateService).delete(5L);

        mockMvc.perform(delete(BASE + "/5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unexpectedErrorReturns500WithGenericMessage() throws Exception {
        when(candidateService.getById(1L)).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private static String withVersion(long version) {
        return VALID_BODY.replace("\"firstName\"", "\"version\": " + version + ",\n  \"firstName\"");
    }

    private static CandidateResponse response(Long id, Long version) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new CandidateResponse(id, "Jane", "Doe", "jane@example.com", null, null, null, null,
                new BigDecimal("5.5"), 30, CandidateStatus.NEW, null, List.of("java", "spring"), now, now, version);
    }
}
