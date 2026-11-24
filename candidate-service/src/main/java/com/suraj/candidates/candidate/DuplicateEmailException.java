package com.suraj.candidates.candidate;

/** The message deliberately does not contain the email (PII). */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("A candidate with this email already exists");
    }
}
