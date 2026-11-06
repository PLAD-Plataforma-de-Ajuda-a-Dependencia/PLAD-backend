package plad.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.Meeting;
import plad.entity.MeetingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    // the host's name is read for every row: fetch psychologist and user together, no N+1
    @EntityGraph(attributePaths = {"psychologist", "psychologist.user"})
    @Query("select m from Meeting m where m.startsAt >= :from and m.startsAt < :to order by m.startsAt, m.id")
    List<Meeting> findInWindow(@Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = {"psychologist", "psychologist.user"})
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findWithHostById(@Param("id") Long id);

    // Locks the meeting row until the transaction ends. Registering and editing the capacity both take it, so the
    // "count the registered, compare with the capacity, insert" sequence of two requests runs one after the other
    // and the last seat goes to exactly one of them. No entity graph: FOR UPDATE cannot sit on an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findForUpdateById(@Param("id") Long id);

    // intervals are [start, end): a meeting that ends when the other starts does not overlap it.
    // excludeId is -1 on create (a real id never is), so there is no "is null" parameter for Postgres to type
    @Query("""
            select count(m) > 0 from Meeting m
            where m.psychologist.userId = :psychologistId and m.status = :status
              and m.startsAt < :end and m.endsAt > :start and m.id <> :excludeId""")
    boolean existsOverlap(@Param("psychologistId") Long psychologistId, @Param("status") MeetingStatus status,
                          @Param("start") Instant start, @Param("end") Instant end,
                          @Param("excludeId") Long excludeId);
}
