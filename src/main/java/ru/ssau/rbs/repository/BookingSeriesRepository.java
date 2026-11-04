package ru.ssau.rbs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ssau.rbs.domain.entity.BookingSeries;

import java.util.List;
import java.util.Optional;

public interface BookingSeriesRepository extends JpaRepository<BookingSeries, Long> {

    List<BookingSeries> findAllByUserId(Long userId);

    List<BookingSeries> findAllByResourceId(Long resourceId);

    Optional<BookingSeries> findByIdAndUserId(Long id, Long userId);
}
