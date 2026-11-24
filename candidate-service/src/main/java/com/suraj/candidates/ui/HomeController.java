package com.suraj.candidates.ui;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page UI at "/" directly. Spring Boot's welcome page would only forward
 * to index.html, which MockMvc records but does not follow.
 */
@Controller
public class HomeController {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @GetMapping("/")
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(INDEX);
    }
}
