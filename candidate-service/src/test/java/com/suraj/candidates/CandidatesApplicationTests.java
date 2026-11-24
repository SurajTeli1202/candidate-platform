package com.suraj.candidates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:candidates-application-smoke;DB_CLOSE_DELAY=-1")
class CandidatesApplicationTests {

    private final DataSource dataSource;

    @Autowired
    CandidatesApplicationTests(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void contextLoadsWithAnIsolatedInMemoryDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo("jdbc:h2:mem:candidates-application-smoke");
        }
    }
}
