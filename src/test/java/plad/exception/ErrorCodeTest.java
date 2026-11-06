package plad.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// each custom exception owns its code; clients switch on it, so renaming one is a breaking API change
class ErrorCodeTest {

    @Test
    void everyCustomExceptionExposesItsOwnCode() {
        assertThat(new EmailAlreadyInUseException("m").getCode()).isEqualTo(ErrorCode.EMAIL_IN_USE);
        assertThat(new InvalidCredentialsException("m").getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(new InvalidRefreshTokenException("m").getCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(new InvalidPasswordChangeException("m").getCode()).isEqualTo(ErrorCode.INVALID_PASSWORD_CHANGE);
        assertThat(new InvalidRegistrationException("m").getCode()).isEqualTo(ErrorCode.INVALID_REGISTRATION);
        assertThat(new InvalidTriageException("m").getCode()).isEqualTo(ErrorCode.INVALID_TRIAGE);
        assertThat(new ConsentRequiredException("m").getCode()).isEqualTo(ErrorCode.CONSENT_REQUIRED);
        assertThat(new TriageAlreadyExistException("m").getCode()).isEqualTo(ErrorCode.TRIAGE_ALREADY_EXISTS);
        assertThat(new TriageNotFoundException("m").getCode()).isEqualTo(ErrorCode.TRIAGE_NOT_FOUND);
        assertThat(new InvalidMeetingException("capacity", "m").getCode()).isEqualTo(ErrorCode.INVALID_MEETING);
        assertThat(new MeetingNotFoundException("m").getCode()).isEqualTo(ErrorCode.MEETING_NOT_FOUND);
        assertThat(new MeetingConflictException("m").getCode()).isEqualTo(ErrorCode.MEETING_CONFLICT);
        assertThat(new ForbiddenActionException("m").getCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(new TooManyRequestException("m", 1).getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
    }
}
