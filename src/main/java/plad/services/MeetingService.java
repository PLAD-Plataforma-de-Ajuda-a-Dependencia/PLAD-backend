package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.MeetingRequestDTO;
import plad.dto.MeetingResponseDTO;
import plad.entity.Meeting;
import plad.entity.MeetingStatus;
import plad.entity.Psychologist;
import plad.exception.ForbiddenActionException;
import plad.exception.MeetingCapacityTooLowException;
import plad.exception.MeetingConflictException;
import plad.exception.MeetingNotFoundException;
import plad.repositories.MeetingRegistrationRepository;
import plad.repositories.MeetingRepository;
import plad.services.room.MeetingRoomGateway;
import plad.services.room.MeetingRooms;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MeetingService {

    private static final long NO_MEETING = -1L; // existsOverlap's "exclude nothing"

    private final MeetingRepository meetingRepository;
    private final MeetingRegistrationRepository registrationRepository;
    private final PsychologistService psychologistService;
    private final MeetingRoomGateway roomGateway;
    private final Clock clock;

    public MeetingService(MeetingRepository meetingRepository, MeetingRegistrationRepository registrationRepository,
                          PsychologistService psychologistService, MeetingRoomGateway roomGateway, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.registrationRepository = registrationRepository;
        this.psychologistService = psychologistService;
        this.roomGateway = roomGateway;
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
        // a meeting that was just created has nobody registered
        return toDto(meetingRepository.saveAndFlush(meeting), userId, 0, false);
    }

    // only the host, only while scheduled and before it starts
    @Transactional
    public MeetingResponseDTO update(Long userId, Long id, MeetingRequestDTO request) {
        psychologistService.requireCanAct(userId);
        // the lock is shared with registering: no dependent can take a seat between the count below and the save
        meetingRepository.findForUpdateById(id)
                .orElseThrow(() -> new MeetingNotFoundException("Meeting not found"));
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
        long registered = registrationRepository.countByMeetingId(id);
        if (request.capacity() < registered) {
            throw new MeetingCapacityTooLowException("The capacity cannot be lower than the number of registered dependents");
        }
        requireFreeSlot(userId, request.startsAt(), request.endsAt(), meeting.getId());

        apply(meeting, request);
        return describe(meetingRepository.saveAndFlush(meeting), userId);
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
        Meeting saved = meetingRepository.saveAndFlush(meeting);
        // a meeting cancelled while people may already be inside must not leave them there; before the join window
        // nobody can be in the room, so the provider is not called (and cancelling does not depend on it)
        if (MeetingRules.joinWindowOpen(saved.getStartsAt(), saved.getEndsAt(), now)) {
            closeRoom(saved);
        }
        return describe(saved, userId);
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
        Meeting saved = meetingRepository.saveAndFlush(meeting);
        closeRoom(saved);
        return describe(saved, userId);
    }

    // Inside this transaction on purpose: if the provider cannot be reached the answer is 503 and the meeting stays
    // scheduled, so the host tries again. Closing quietly in the background could leave dependents in a room with no
    // host and nobody the wiser.
    private void closeRoom(Meeting meeting) {
        roomGateway.closeRoom(MeetingRooms.name(meeting.getId()));
    }

    // meetings are public to every authenticated role; cancelled and ended ones are listed too (the calendar shows them)
    @Transactional(readOnly = true)
    public List<MeetingResponseDTO> list(Long userId, Instant from, Instant to) {
        MeetingRules.validateWindow(from, to);
        List<Meeting> meetings = meetingRepository.findInWindow(from, to);
        if (meetings.isEmpty()) {
            return List.of();
        }
        // two queries for the whole window, not two per meeting
        List<Long> ids = meetings.stream().map(Meeting::getId).toList();
        Map<Long, Integer> registeredCounts = registrationRepository.countByMeetingIds(ids).stream()
                .collect(Collectors.toMap(MeetingRegistrationRepository.MeetingCount::getMeetingId,
                        count -> (int) count.getTotal()));
        Set<Long> mine = new HashSet<>(registrationRepository.findRegisteredMeetingIds(userId, ids));
        return meetings.stream()
                .map(meeting -> toDto(meeting, userId, registeredCounts.getOrDefault(meeting.getId(), 0),
                        mine.contains(meeting.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingResponseDTO find(Long userId, Long id) {
        return describe(load(id), userId);
    }

    // the response for one meeting as seen by this caller: how many are registered and whether the caller is one of them
    MeetingResponseDTO describe(Meeting meeting, Long userId) {
        return toDto(meeting, userId, (int) registrationRepository.countByMeetingId(meeting.getId()),
                registrationRepository.isRegistered(meeting.getId(), userId));
    }

    private Meeting load(Long id) {
        return meetingRepository.findWithHostById(id)
                .orElseThrow(() -> new MeetingNotFoundException("Meeting not found"));
    }

    // 403 unless the caller hosts the meeting; also the gate of the participants list
    Meeting ownedBy(Long userId, Long id) {
        Meeting meeting = load(id);
        if (!meeting.getPsychologist().getUserId().equals(userId)) {
            throw new ForbiddenActionException("Only the host can do this");
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

    private static MeetingResponseDTO toDto(Meeting meeting, Long userId, int registeredCount, boolean registeredByMe) {
        Psychologist host = meeting.getPsychologist();
        return new MeetingResponseDTO(meeting.getId(), meeting.getTitle(), meeting.getDescription(),
                meeting.getStartsAt(), meeting.getEndsAt(), meeting.getCapacity(), meeting.getStatus(),
                host.getUser().getRealName(), host.getUserId().equals(userId), registeredCount, registeredByMe);
    }
}
