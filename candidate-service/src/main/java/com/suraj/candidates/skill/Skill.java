package com.suraj.candidates.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "skill")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    protected Skill() {
    }

    public Skill(String name) {
        this.name = normalize(name);
    }

    public static String normalize(String name) {
        Objects.requireNonNull(name, "Skill name must not be null");
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Skill name must not be blank");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // Identity is the normalized, unique and immutable name, so it is stable before and after persist.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Skill other)) {
            return false;
        }
        return Objects.nonNull(name) && name.equals(other.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
