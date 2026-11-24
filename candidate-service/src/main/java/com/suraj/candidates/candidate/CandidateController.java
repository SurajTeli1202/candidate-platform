package com.suraj.candidates.candidate;

import com.suraj.candidates.candidate.dto.CandidateRequest;
import com.suraj.candidates.candidate.dto.CandidateResponse;
import com.suraj.candidates.candidate.dto.CandidateSearchCriteria;
import com.suraj.candidates.candidate.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/candidates")
@ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "500", description = "Unexpected error",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @PostMapping
    @Operation(summary = "Create a candidate")
    @ApiResponse(responseCode = "201", description = "Candidate created")
    @ApiResponse(responseCode = "409", description = "Email or data conflict",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<CandidateResponse> create(@Valid @RequestBody CandidateRequest request) {
        CandidateResponse created = candidateService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Search and filter active candidates")
    @ApiResponse(responseCode = "200", description = "Matching candidates and page metadata")
    public PageResponse<CandidateResponse> search(@Valid @ParameterObject @ModelAttribute CandidateSearchCriteria criteria) {
        return candidateService.search(criteria);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active candidate")
    @ApiResponse(responseCode = "200", description = "Candidate returned")
    @ApiResponse(responseCode = "404", description = "Candidate not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public CandidateResponse getById(@PathVariable Long id) {
        return candidateService.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a candidate using its current version")
    @ApiResponse(responseCode = "200", description = "Candidate updated")
    @ApiResponse(responseCode = "404", description = "Candidate not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Stale version, duplicate email or data conflict",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public CandidateResponse update(@PathVariable Long id,
                                    @Validated(CandidateRequest.OnUpdate.class) @RequestBody CandidateRequest request) {
        return candidateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a candidate")
    @ApiResponse(responseCode = "204", description = "Candidate soft-deleted", content = @Content)
    @ApiResponse(responseCode = "404", description = "Candidate not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Concurrent modification or data conflict",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public void delete(@PathVariable Long id) {
        candidateService.delete(id);
    }
}
