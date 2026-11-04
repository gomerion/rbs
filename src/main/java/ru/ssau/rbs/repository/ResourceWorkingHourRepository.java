package ru.ssau.rbs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.ssau.rbs.domain.entity.ResourceWorkingHour;
import ru.ssau.rbs.domain.entity.ResourceWorkingHourId;

import java.util.List;

public interface ResourceWorkingHourRepository extends JpaRepository<ResourceWorkingHour, ResourceWorkingHourId> {

    @Query("""
            select w from ResourceWorkingHour w
            where w.id.resourceId = :resourceId
            order by w.id.weekday, w.id.startTime
            """)
    List<ResourceWorkingHour> findAllByResourceId(@Param("resourceId") Long resourceId);

    @Query("""
            select w from ResourceWorkingHour w
            where w.id.resourceId = :resourceId and w.id.weekday = :weekday
            order by w.id.startTime
            """)
    List<ResourceWorkingHour> findAllByResourceIdAndWeekday(@Param("resourceId") Long resourceId,
                                                           @Param("weekday") Short weekday);

    @Query("""
            select w from ResourceWorkingHour w
            where w.id.resourceId = :resourceId and w.id.weekday in :weekdays
            order by w.id.weekday, w.id.startTime
            """)
    List<ResourceWorkingHour> findAllByResourceIdAndWeekdayIn(@Param("resourceId") Long resourceId,
                                                             @Param("weekdays") List<Short> weekdays);

    @Transactional
    @Modifying
    @Query("delete from ResourceWorkingHour w where w.id.resourceId = :resourceId")
    int deleteAllByResourceId(@Param("resourceId") Long resourceId);
}
