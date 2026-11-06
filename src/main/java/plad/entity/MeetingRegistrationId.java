package plad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MeetingRegistrationId implements Serializable {

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "dependent_id")
    private Long dependentId;

    protected MeetingRegistrationId() {
    }

    public Long getMeetingId() {
        return meetingId;
    }

    public Long getDependentId() {
        return dependentId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MeetingRegistrationId that
                && Objects.equals(meetingId, that.meetingId) && Objects.equals(dependentId, that.dependentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meetingId, dependentId);
    }
}
