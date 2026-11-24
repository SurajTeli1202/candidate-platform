package com.suraj.candidates.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Never call {@code delete*} / {@code deleteAll*} methods; deletion is soft and must go through
 * {@link Candidate#markDeleted()} in the service.
 */
public interface CandidateRepository extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    @Query(value = "SELECT COUNT(*) FROM candidate", nativeQuery = true)
    long countIncludingDeleted();

    // Native queries bypass @SQLRestriction, so soft-deleted rows are counted too
    // (their emails stay reserved by the unique constraint).
    @Query(value = "SELECT COUNT(*) FROM candidate WHERE email = :email", nativeQuery = true)
    long countByEmailIncludingDeleted(@Param("email") String email);

    @Query(value = "SELECT COUNT(*) FROM candidate WHERE email = :email AND id <> :id", nativeQuery = true)
    long countByEmailIncludingDeletedExcludingId(@Param("email") String email, @Param("id") Long id);
}
