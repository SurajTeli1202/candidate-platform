package com.suraj.candidates.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept separate from the main application class so that test slices which do not
 * bootstrap JPA (e.g. {@code @WebMvcTest}) don't fail trying to enable auditing.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingConfig {
}
