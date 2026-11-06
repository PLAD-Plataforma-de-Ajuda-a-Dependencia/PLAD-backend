package plad.entity;

// constants match meetings.status (VARCHAR + CHECK) and the JSON values; not the orphan status_reuniao enum from V1
public enum MeetingStatus {
    scheduled,
    cancelled,
    ended
}
