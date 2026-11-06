package plad.entity;

// stored as VARCHAR in audit_log.action (not a Postgres enum), so the constants are UPPER_CASE
// like the other codes in the API; a new action needs no migration
public enum AuditAction {
    TRIAGE_SUBMITTED(false),
    TRIAGE_RESUBMITTED(false),
    // an admin or psychologist read the triage (GET /triages, GET /triages/{id}); shown to the owner in GET /me/access-log
    TRIAGE_VIEWED(true),
    // a dependent drew a new anonymous nickname (POST /me/nickname/reroll); the nickname is never stored in the entry
    NICKNAME_REROLLED(false),
    // the host psychologist listed who is registered in their meeting (GET /meetings/{id}/participants); one entry per
    // dependent listed, shown to each of them in GET /me/access-log. details: meeting_id and actor_role only
    MEETING_PARTICIPANTS_VIEWED(true);

    private final boolean thirdPartyRead;

    AuditAction(boolean thirdPartyRead) {
        this.thirdPartyRead = thirdPartyRead;
    }

    public boolean isThirdPartyRead() {
        return thirdPartyRead;
    }
}
