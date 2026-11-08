package plad.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.MeetingStatus;
import plad.entity.Triage;

import java.util.Optional;

public interface TriageRepository extends JpaRepository<Triage,Long> {
    // row lock (select ... for update) so concurrent resubmits of the same triage run one after the other
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Triage> findForUpdateByDependentUserId(Long dependentUserId);

    boolean existsByDependentUserId(Long dependentUserId);

    Optional<Triage> findByDependentUserId(Long dependentUserId);

    // dependent and its user (real name) are fetched with the page, so no extra query per row
    @EntityGraph(attributePaths = {"dependent", "dependent.user"})
    Page<Triage> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"dependent", "dependent.user"})
    Optional<Triage> findWithDependentById(Long id);

    // What a psychologist may read: only the triage of a dependent registered in a meeting of theirs whose status is
    // not `excluded` (see the default methods below). The filter sits in the query, so a triage outside it is never
    // loaded. EXISTS, not a join: a dependent in several of the psychologist's meetings must come back once, with the
    // count of the page right. The sort still comes from the Pageable.
    @EntityGraph(attributePaths = {"dependent", "dependent.user"})
    @Query(value = """
            select t from Triage t
            where exists (
                select 1 from MeetingRegistration r join r.meeting m
                where r.id.dependentId = t.dependent.userId
                  and m.psychologist.userId = :psychologistId and m.status <> :excluded)""",
            countQuery = """
            select count(t) from Triage t
            where exists (
                select 1 from MeetingRegistration r join r.meeting m
                where r.id.dependentId = t.dependent.userId
                  and m.psychologist.userId = :psychologistId and m.status <> :excluded)""")
    Page<Triage> findVisibleToPsychologist(@Param("psychologistId") Long psychologistId,
                                           @Param("excluded") MeetingStatus excluded, Pageable pageable);

    @EntityGraph(attributePaths = {"dependent", "dependent.user"})
    @Query("""
            select t from Triage t
            where t.id = :id
              and exists (
                select 1 from MeetingRegistration r join r.meeting m
                where r.id.dependentId = t.dependent.userId
                  and m.psychologist.userId = :psychologistId and m.status <> :excluded)""")
    Optional<Triage> findVisibleToPsychologistById(@Param("psychologistId") Long psychologistId,
                                                   @Param("excluded") MeetingStatus excluded, @Param("id") Long id);

    // The rule in one place: a cancelled meeting gives no access (scheduled and ended ones do).
    default Page<Triage> findVisibleToPsychologist(Long psychologistId, Pageable pageable) {
        return findVisibleToPsychologist(psychologistId, MeetingStatus.cancelled, pageable);
    }

    default Optional<Triage> findVisibleToPsychologistById(Long psychologistId, Long id) {
        return findVisibleToPsychologistById(psychologistId, MeetingStatus.cancelled, id);
    }
}
