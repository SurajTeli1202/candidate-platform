package com.suraj.candidates.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillTest {

    @Test
    void nameIsTrimmedAndLowercased() {
        Skill skill = new Skill("  JaVa \t");

        assertThat(skill.getName()).isEqualTo("java");
    }

    @Test
    void blankNameThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new Skill(" \t\n "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullNameThrowsNullPointerException() {
        assertThatThrownBy(() -> new Skill(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void namesDifferingOnlyInCaseAndWhitespaceAreEqualWithTheSameHashCode() {
        Skill first = new Skill("  JAVA ");
        Skill second = new Skill("java");

        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(first);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
