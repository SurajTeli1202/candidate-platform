package com.suraj.candidates.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI candidateOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Candidate Management API")
                .description("REST API for managing and searching candidate records.")
                .version("v1"));
    }
}
