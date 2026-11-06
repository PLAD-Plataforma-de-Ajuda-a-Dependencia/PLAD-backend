package plad.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.MeetingRegistration;
import plad.entity.MeetingRegistrationId;

import java.util.Collection;
import java.util.List;

public interface MeetingRegistrationRepository extends JpaRepository<MeetingRegistration, MeetingRegistrationId> {

    // registrations per meeting for a whole list in one query (no N+1); meetings with none are absent from the result
    interface MeetingCount {
        Long getMeetingId();

        long getTotal();
    }

    @Query("select r.id.meetingId as meetingId, count(r) as total from MeetingRegistration r "
            + "where r.id.meetingId in :meetingIds group by r.id.meetingId")
    List<MeetingCount> countByMeetingIds(@Param("meetingIds") Collection<Long> meetingIds);

    @Query("select count(r) from MeetingRegistration r where r.id.meetingId = :meetingId")
    long countByMeetingId(@Param("meetingId") Long meetingId);

    @Query("select count(r) > 0 from MeetingRegistration r "
            + "where r.id.meetingId = :meetingId and r.id.dependentId = :dependentId")
    boolean isRegistered(@Param("meetingId") Long meetingId, @Param("dependentId") Long dependentId);

    // which of these meetings the dependent is in, for registered_by_me on a list
    @Query("select r.id.meetingId from MeetingRegistration r "
            + "where r.id.dependentId = :dependentId and r.id.meetingId in :meetingIds")
    List<Long> findRegisteredMeetingIds(@Param("dependentId") Long dependentId,
                                        @Param("meetingIds") Collection<Long> meetingIds);

    // psychologist's list: nickname and real name come from the dependent and its user, fetched together
    @EntityGraph(attributePaths = {"dependent", "dependent.user"})
    @Query("select r from MeetingRegistration r where r.id.meetingId = :meetingId "
            + "order by r.registeredAt, r.id.dependentId")
    List<MeetingRegistration> findParticipants(@Param("meetingId") Long meetingId);

    // the data subject's own export
    @EntityGraph(attributePaths = "meeting")
    @Query("select r from MeetingRegistration r where r.id.dependentId = :dependentId "
            + "order by r.registeredAt, r.id.meetingId")
    List<MeetingRegistration> findByDependent(@Param("dependentId") Long dependentId);

    // 0 when the dependent was already registered: a failed INSERT would abort the whole Postgres transaction
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO meeting_registrations (meeting_id, dependent_id) VALUES (:meetingId, :dependentId) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("meetingId") Long meetingId, @Param("dependentId") Long dependentId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM meeting_registrations WHERE meeting_id = :meetingId AND dependent_id = :dependentId",
            nativeQuery = true)
    int deleteRegistration(@Param("meetingId") Long meetingId, @Param("dependentId") Long dependentId);
}
