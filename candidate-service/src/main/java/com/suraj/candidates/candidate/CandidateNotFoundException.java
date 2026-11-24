package com.suraj.candidates.candidate;

public class CandidateNotFoundException extends RuntimeException {

    public CandidateNotFoundException(Long id) {
        super("Candidate " + id + " was not found");
    }
}
