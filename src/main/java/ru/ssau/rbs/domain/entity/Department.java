package ru.ssau.rbs.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department extends AbstractEntity {

    @Column(name = "name", nullable = false, unique = true, columnDefinition = "text")
    private String name;
}
