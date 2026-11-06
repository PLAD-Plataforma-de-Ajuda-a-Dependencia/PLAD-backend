package plad.exception;

import plad.dto.FieldErrorDTO;

import java.util.List;
import java.util.stream.Collectors;

// a meeting rule was broken (duration, capacity, lead time, window...); carries the fields so the form can show each one
public class InvalidMeetingException extends ApiException {

    private final List<FieldErrorDTO> errors;

    public InvalidMeetingException(List<FieldErrorDTO> errors) {
        super(ErrorCode.INVALID_MEETING, errors.stream()
                .map(error -> error.field() + ": " + error.message())
                .collect(Collectors.joining("; ")));
        this.errors = List.copyOf(errors);
    }

    public InvalidMeetingException(String field, String message) {
        this(List.of(new FieldErrorDTO(field, message)));
    }

    public List<FieldErrorDTO> getErrors() {
        return errors;
    }
}
