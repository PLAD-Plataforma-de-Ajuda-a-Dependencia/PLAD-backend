package plad.entity;

// stored as VARCHAR in audit_log.action (not a Postgres enum), so the constants are UPPER_CASE
// like the other codes in the API; a new action needs no migration
public enum AuditAction {
    TRIAGE_SUBMITTED(false),
    TRIAGE_RESUBMITTED(false),
    // someone other than the owner read the triage; shown to the owner in GET /me/access-log
    TRIAGE_READ(true);

    private final boolean thirdPartyRead;

    AuditAction(boolean thirdPartyRead) {
        this.thirdPartyRead = thirdPartyRead;
    }

    public boolean isThirdPartyRead() {
        return thirdPartyRead;
    }
}
