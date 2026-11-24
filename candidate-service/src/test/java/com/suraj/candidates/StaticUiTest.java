package com.suraj.candidates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:candidates-static-ui;DB_CLOSE_DELAY=-1")
@EmbeddedKafka(
        partitions = 3,
        topics = "candidate-events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@AutoConfigureMockMvc
class StaticUiTest {

    private final MockMvc mockMvc;

    @Autowired
    StaticUiTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void rootReturnsTheCandidatePageAsHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<title>Candidate Management</title>")))
                .andExpect(content().string(containsString("src=\"app.js\"")));
    }
}
