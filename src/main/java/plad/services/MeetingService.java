package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.MeetingRequestDTO;
import plad.dto.MeetingResponseDTO;
import plad.entity.Meeting;
import plad.entity.MeetingStatus;
import plad.entity.Psychologist;
import plad.exception.ForbiddenActionException;
import plad.exception.MeetingConflictException;
import plad.exception.MeetingNotFoundException;
import plad.repositories.MeetingRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class MeetingService {

    private static final long NO_MEETING = -1L; // existsOverlap's "exclude nothing"

    private final MeetingRepository meetingRepository;
    private final PsychologistService psychologistService;
    private final Clock clock;

    public MeetingService(MeetingRepository meetingRepository, PsychologistService psychologistService, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.psychologistService = psychologistService;
        this.clock = clock;
    }

    @Transactional
    public MeetingResponseDTO create(Long userId, MeetingRequestDTO request) {
        Psychologist host = psychologistService.requireCanAct(userId);
        MeetingRules.validate(request.startsAt(), request.endsAt(), request.capacity(), clock.instant(), true);
        requireFreeSlot(userId, request.startsAt(), request.endsAt(), NO_MEETING);

        Meeting meeting = new Meeting();
        meeting.setPsychologist(host);
        apply(meeting, request);
        // flush so the exclusion constraint (a race between two requests) fails here, where the handler translates it
        return toDto(meetingRepository.saveAndFlush(meeting), userId);
    }

    // only the host, only while scheduled and before it starts
    @Transactional
    public MeetingResponseDTO update(Long userId, Long id, MeetingRequestDTO request) {
        psychologistService.requireCanAct(userId);
        Meeting meeting = ownedBy(userId, id);
        Instant now = clock.instant();
        if (meeting.getStatus() != MeetingStatus.scheduled) {
            throw new MeetingConflictException("Only a scheduled meeting can be edited");
        }
        if (!now.isBefore(meeting.getStartsAt())) {
            throw new MeetingConflictException("A meeting that already started cannot be edited");
        }
        boolean movesStart = !request.startsAt().equals(meeting.getStartsAt());
        MeetingRules.validate(request.startsAt(), request.endsAt(), request.capacity(), now, movesStart);
        requireFreeSlot(userId, request.startsAt(), request.endsAt(), meeting.getId());

        apply(meeting, request);
        return toDto(meetingRepository.saveAndFlush(meeting), userId);
    }

    // only the host, only while scheduled and before it ends
    @Transactional
    public MeetingResponseDTO cancel(Long userId, Long id) {
        psychologistService.requireCanAct(userId);
        Meeting meeting = ownedBy(userId, id);
        Instant now = clock.instant();
        if (meeting.getStatus() != MeetingStatus.scheduled) {
            throw new MeetingConflictException("Only a scheduled meeting can be cancelled");
        }
        if (!now.isBefore(meeting.getEndsAt())) {
            throw new MeetingConflictException("A meeting that already ended cannot be cancelled");
        }
        meeting.setStatus(MeetingStatus.cancelled);
        meeting.setCancelledAt(now);
        return toDto(meetingRepository.saveAndFlush(meeting), userId);
    }

    // only the host, only while scheduled and once it has started
    @Transactional
    public MeetingResponseDTO end(Long userId, Long id) {
        psychologistService.requireCanAct(userId);
        Meeting meeting = ownedBy(userId, id);
        Instant now = clock.instant();
        if (meeting.getStatus() != MeetingStatus.scheduled) {
            throw new MeetingConflictException("Only a scheduled meeting can be ended");
        }
        if (now.isBefore(meeting.getStartsAt())) {
            throw new MeetingConflictException("A meeting that has not started cannot be ended");
        }
        meeting.setStatus(MeetingStatus.ended);
        meeting.setEndedAt(now);
        return toDto(meetingRepository.saveAndFlush(meeting), userId);
    }

    // meetings are public to every authenticated role; cancelled and ended ones are listed too (the calendar shows them)
    @Transactional(readOnly = true)
    public List<MeetingResponseDTO> list(Long userId, Instant from, Instant to) {
        MeetingRules.validateWindow(from, to);
        return meetingRepository.findInWindow(from, to).stream()
                .map(meeting -> toDto(meeting, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingResponseDTO find(Long userId, Long id) {
        return toDto(load(id), userId);
    }

    private Meeting load(Long id) {
        return meetingRepository.findWithHostById(id)
                .orElseThrow(() -> new MeetingNotFoundException("Meeting not found"));
    }

    private Meeting ownedBy(Long userId, Long id) {
        Meeting meeting = load(id);
        if (!meeting.getPsychologist().getUserId().equals(userId)) {
            throw new ForbiddenActionException("Only the host can change this meeting");
        }
        return meeting;
    }

    // a clean 409 before the INSERT/UPDATE: a violated exclusion constraint aborts the whole Postgres transaction
    private void requireFreeSlot(Long psychologistId, Instant startsAt, Instant endsAt, Long excludeId) {
        if (meetingRepository.existsOverlap(psychologistId, MeetingStatus.scheduled, startsAt, endsAt, excludeId)) {
            throw new MeetingConflictException("You already have a scheduled meeting that overlaps this time");
        }
    }

    private static void apply(Meeting meeting, MeetingRequestDTO request) {
        meeting.setTitle(request.title().strip());
        String description = request.description() == null ? "" : request.description().strip();
        meeting.setDescription(description.isEmpty() ? null : description);
        meeting.setStartsAt(request.startsAt());
        meeting.setEndsAt(request.endsAt());
        meeting.setCapacity(request.capacity());
    }

    private static MeetingResponseDTO toDto(Meeting meeting, Long userId) {
        Psychologist host = meeting.getPsychologist();
        return new MeetingResponseDTO(meeting.getId(), meeting.getTitle(), meeting.getDescription(),
                meeting.getStartsAt(), meeting.getEndsAt(), meeting.getCapacity(), meeting.getStatus(),
                host.getUser().getRealName(), host.getUserId().equals(userId));
    }
}
