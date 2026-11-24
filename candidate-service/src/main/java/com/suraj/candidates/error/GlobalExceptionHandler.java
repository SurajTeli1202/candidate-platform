package com.suraj.candidates.error;

import com.suraj.candidates.candidate.CandidateNotFoundException;
import com.suraj.candidates.candidate.DuplicateEmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Returns RFC 7807 ProblemDetail for every error. Extends ResponseEntityExceptionHandler so that
 * Spring MVC's own exceptions (405, 415, type mismatch, ...) keep their proper status codes.
 * Rejected values are never echoed back or logged, because they may contain PII.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldError(String field, String message) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(),
                        error.isBindingFailure() ? "Invalid value for this parameter" : error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, notReadableDetail(ex));
        problem.setTitle("Malformed request");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(CandidateNotFoundException.class)
    public ProblemDetail handleNotFound(CandidateNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Candidate not found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate email", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The candidate was modified by someone else. Reload it and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // The exception message may contain column values (e.g. the email), so it is not logged.
        log.warn("Data integrity violation ({})", ex.getMostSpecificCause().getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing data.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private static String notReadableDetail(HttpMessageNotReadableException ex) {
        if (ex.getMostSpecificCause() instanceof InvalidFormatException invalid
                && invalid.getTargetType() != null && invalid.getTargetType().isEnum()) {
            String field = invalid.getPath().isEmpty() ? "value"
                    : invalid.getPath().getLast().getPropertyName();
            return "Invalid value for '" + field + "'. Allowed values: "
                    + Arrays.toString(invalid.getTargetType().getEnumConstants());
        }
        return "Malformed JSON request";
    }
}
