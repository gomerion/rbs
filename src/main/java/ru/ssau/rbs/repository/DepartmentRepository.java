package ru.ssau.rbs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ssau.rbs.domain.entity.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByName(String name);

    boolean existsByName(String name);

    List<Department> findAllByOrderByNameAsc();
}
