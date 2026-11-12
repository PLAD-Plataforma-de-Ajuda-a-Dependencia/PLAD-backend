package plad.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.Triage;

import java.time.Instant;
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

    // What a psychologist may read (decision 13): only the triage of a dependent registered in a meeting of theirs that
    // was not cancelled and whose ends_at is not before `accessCutoff`, the start of the last day the access lasts
    // (MeetingRules#triageAccessCutoff: 30 calendar days in Sao Paulo after the end). Scheduled and ended meetings count
    // alike, so a meeting nobody ended also stops giving access. The filter sits in the query, so a triage outside it is
    // never loaded. EXISTS, not a join: a dependent in several of the psychologist's meetings must come back once, with
    // the count of the page right. The sort still comes from the Pageable.
    @EntityGraph(attributePaths = {"dependent", "dependent.user"})
    @Query(value = """
            select t from Triage t
            where exists (
                select 1 from MeetingRegistration r join r.meeting m
                where r.id.dependentId = t.dependent.userId
                  and m.psychologist.userId = :psychologistId
                  and m.status <> plad.entity.MeetingStatus.cancelled
                  and m.endsAt >= :accessCutoff)""",
            countQuery = """
            select count(t) from Triage t
            where exists (
                select 1 from MeetingRegistration r join r.meeting m
                where r.id.dependentId = t.dependent.userId
                  and m.psychologist.userId = :psychologistId
                  and m.status <> plad.entity.MeetingStatus.cancelled
                  and m.endsAt >= :accessCutoff)""")
    Page<Triage> findVisibleToPsychologist(@Param("psychologistId") Long psychologistId,
                                           @Param("accessCutoff") Instant accessCutoff, Pageable pageable);

    @EntityGraph(attributePaths = {"dependent", "dependent.user"})
    @Query("""
            select t from Triage t
            where t.id = :id
              and exists (
                select 1 from MeetingRegistration r join r.meeting m
                where r.id.dependentId = t.dependent.userId
                  and m.psychologist.userId = :psychologistId
                  and m.status <> plad.entity.MeetingStatus.cancelled
                  and m.endsAt >= :accessCutoff)""")
    Optional<Triage> findVisibleToPsychologistById(@Param("psychologistId") Long psychologistId,
                                                   @Param("accessCutoff") Instant accessCutoff, @Param("id") Long id);

    // Revoking the health data consent (decision 35): the answers (result_questions_triages) go with the row through the
    // ON DELETE CASCADE of V4, which the persistence context knows nothing about, so it is cleared afterwards
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM triages WHERE dependent_id = :dependentId", nativeQuery = true)
    int deleteByDependentId(@Param("dependentId") Long dependentId);
}
