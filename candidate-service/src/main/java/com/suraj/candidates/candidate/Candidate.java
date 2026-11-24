package com.suraj.candidates.candidate;

import com.suraj.candidates.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Candidate record.
 *
 * <p><b>Soft delete:</b> a candidate is deleted by calling {@link #markDeleted()} and saving it.
 * That is a normal UPDATE: {@code @Version} is checked and incremented (a stale version fails with
 * an optimistic locking exception), {@code updated_at} is refreshed by auditing, and the
 * {@code candidate_skill} rows are kept. Never use the repository's {@code delete*} methods: they
 * would physically delete the row (and Hibernate would remove the join-table rows first).
 *
 * <p><b>Hiding deleted rows:</b> {@link SQLRestriction} appends {@code deleted = FALSE} to every
 * Hibernate-generated query for this entity (find, JPQL, Criteria/Specifications, derived queries).
 * Native SQL queries are NOT filtered and must add the condition themselves. A future "restore"
 * feature therefore needs a native query, because the entity can't be loaded once deleted.
 *
 * <p><b>Unique email:</b> {@code uq_candidate_email} is a plain unique constraint over ALL rows,
 * including soft-deleted ones, so the email of a deleted candidate stays reserved. Because the
 * restriction hides deleted rows, a repository "exists by email" check would miss them. The
 * service's duplicate-email check must therefore bypass the restriction (e.g. a native query)
 * and return 409 Conflict; {@code DataIntegrityViolationException} must also be mapped to 409
 * as a fallback for concurrent inserts.
 */
@Entity
@Table(name = "candidate")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted = FALSE")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "current_title", length = 150)
    private String currentTitle;

    @Column(name = "current_company", length = 150)
    private String currentCompany;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "experience_years", precision = 4, scale = 1)
    private BigDecimal experienceYears;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CandidateStatus status = CandidateStatus.NEW;

    @Column(name = "notes", length = 4000)
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    // Skills are shared reference data, so no cascade: they must be persisted/looked up separately.
    @ManyToMany
    @JoinTable(
            name = "candidate_skill",
            joinColumns = @JoinColumn(name = "candidate_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new HashSet<>();

    protected Candidate() {
    }

    public Candidate(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public void addSkill(Skill skill) {
        skills.add(skill);
    }

    // Mutates the managed collection instead of replacing it, so Hibernate only syncs the difference.
    public void replaceSkills(Set<Skill> newSkills) {
        skills.clear();
        skills.addAll(newSkills);
    }

    public Set<Skill> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public void setCurrentTitle(String currentTitle) {
        this.currentTitle = currentTitle;
    }

    public String getCurrentCompany() {
        return currentCompany;
    }

    public void setCurrentCompany(String currentCompany) {
        this.currentCompany = currentCompany;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public BigDecimal getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(BigDecimal experienceYears) {
        this.experienceYears = experienceYears;
    }

    public Integer getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(Integer noticePeriodDays) {
        this.noticePeriodDays = noticePeriodDays;
    }

    public CandidateStatus getStatus() {
        return status;
    }

    public void setStatus(CandidateStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
