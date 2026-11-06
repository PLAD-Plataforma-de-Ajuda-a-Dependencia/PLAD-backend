package plad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

// Read-only view of meeting_registrations (participants, export). Rows are written and deleted by
// MeetingRegistrationRepository's native queries, never by this entity.
@Entity
@Immutable
@Table(name = "meeting_registrations")
public class MeetingRegistration {

    @EmbeddedId
    private MeetingRegistrationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("meetingId")
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("dependentId")
    @JoinColumn(name = "dependent_id")
    private Dependent dependent;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected MeetingRegistration() {
    }

    public MeetingRegistrationId getId() {
        return id;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public Dependent getDependent() {
        return dependent;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
