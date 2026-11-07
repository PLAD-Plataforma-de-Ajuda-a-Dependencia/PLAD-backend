package plad.services.room;

// One LiveKit room per meeting. The name is derived from the meeting id only: no person is in it.
public final class MeetingRooms {

    private static final String PREFIX = "plad-meeting-";

    private MeetingRooms() {
    }

    public static String name(long meetingId) {
        return PREFIX + meetingId;
    }
}
