package com.suraj.candidates.config;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:candidates-openapi;DB_CLOSE_DELAY=-1")
@EmbeddedKafka(
        partitions = 3,
        topics = "candidate-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OpenApiIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;

    @Autowired
    OpenApiIntegrationTest(@LocalServerPort int port) {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void h2ConsoleIsDisabledWithoutDemoProfile() throws Exception {
        assertThat(get("/h2-console/").statusCode()).isEqualTo(404);
    }

    @Test
    void swaggerUiRedirectsToWorkingUi() throws Exception {
        HttpResponse<String> redirect = get("/swagger-ui.html");

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("location")).hasValue("/swagger-ui/index.html");
        assertThat(redirect.headers().firstValue("X-Request-Id")).isPresent();

        HttpResponse<String> ui = get("/swagger-ui/index.html");
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("Swagger UI");
    }

    @Test
    void apiDocsContainMetadataAndDocumentedSearchParameters() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).contains("application/json");
        DocumentContext document = JsonPath.parse(response.body());
        assertThat(document.<String>read("$.info.title")).isEqualTo("Candidate Management API");
        assertThat(document.<String>read("$.info.version")).isEqualTo("v1");
        List<String> parameterNames = document.read("$.paths['/api/v1/candidates'].get.parameters[*].name");
        assertThat(parameterNames).containsExactlyInAnyOrder(
                "q", "status", "location", "skills", "skillMatch", "minExperience",
                "maxExperience", "maxNoticePeriod", "page", "size", "sort");
        List<Map<String, Object>> parameters = document.read("$.paths['/api/v1/candidates'].get.parameters");
        assertThat(parameters).allSatisfy(parameter -> {
            assertThat(parameter).containsEntry("in", "query");
            assertThat(parameter.get("description")).isInstanceOf(String.class).isNotEqualTo("");
            assertThat(parameter).containsEntry("required", false);
        });
        assertThat(document.<List<Integer>>read("$.paths['/api/v1/candidates'].get.parameters"
                + "[?(@.name=='page')].schema.default")).containsExactly(0);
        assertThat(document.<List<Integer>>read("$.paths['/api/v1/candidates'].get.parameters"
                + "[?(@.name=='size')].schema.default")).containsExactly(20);
        assertThat(document.<List<String>>read("$.paths['/api/v1/candidates'].get.parameters"
                + "[?(@.name=='sort')].schema.default")).containsExactly("createdAt,desc");
        assertThat(document.<List<String>>read("$.paths['/api/v1/candidates'].get.parameters"
                + "[?(@.name=='skillMatch')].schema.default")).containsExactly("ALL");
    }

    @Test
    void apiDocsDescribeRealStatusesAndProblemDetailErrors() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        DocumentContext document = JsonPath.parse(response.body());

        assertResponses(document, "/api/v1/candidates", "post", "201", "400", "409", "500");
        assertResponses(document, "/api/v1/candidates", "get", "200", "400", "500");
        assertResponses(document, "/api/v1/candidates/{id}", "get", "200", "400", "404", "500");
        assertResponses(document, "/api/v1/candidates/{id}", "put", "200", "400", "404", "409", "500");
        assertResponses(document, "/api/v1/candidates/{id}", "delete", "204", "400", "404", "409", "500");
        assertThat(document.<List<String>>read("$.paths['/api/v1/candidates'].post.responses['201']"
                + ".content.*.schema['$ref']")).containsExactly("#/components/schemas/CandidateResponse");
        assertThat(document.<String>read("$.paths['/api/v1/candidates'].get.responses['400']"
                + ".content['application/problem+json'].schema['$ref']")).endsWith("/ProblemDetail");
        Map<String, Object> noContent = document.read("$.paths['/api/v1/candidates/{id}'].delete.responses['204']");
        assertThat(noContent).doesNotContainKey("content");
    }

    private void assertResponses(DocumentContext document, String path, String method, String... statuses) {
        Map<String, Object> responses = document.read("$.paths['" + path + "']." + method + ".responses");
        assertThat(responses.keySet()).containsExactlyInAnyOrder(statuses);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
