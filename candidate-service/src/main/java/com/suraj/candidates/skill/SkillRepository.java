package com.suraj.candidates.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    /** Names must already be normalized (see {@link Skill#normalize(String)}). */
    List<Skill> findByNameIn(Collection<String> names);
}
