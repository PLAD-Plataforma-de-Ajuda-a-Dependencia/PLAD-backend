package plad.controllers;

import org.springframework.jdbc.core.JdbcTemplate;
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Users, meetings and registrations for the live room tests. Plain SQL for meetings and registrations on purpose: a
// meeting that already started cannot be created through the API (60 minutes of lead time), and the rows are what the
// room rules read.
final class LiveRoomFixtures {

    private final UserRepository userRepository;
    private final DependentRepository dependentRepository;
    private final PsychologistRepository psychologistRepository;
    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;

    LiveRoomFixtures(UserRepository userRepository, DependentRepository dependentRepository,
                     PsychologistRepository psychologistRepository, JdbcTemplate jdbcTemplate, JwtService jwtService) {
        this.userRepository = userRepository;
        this.dependentRepository = dependentRepository;
        this.psychologistRepository = psychologistRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
    }

    User psychologist(String realName) {
        User user = user(UserType.psicologo, realName);
        Psychologist row = new Psychologist();
        row.setUser(user);
        row.setCredentialType(CrmCrpType.CRP);
        row.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.saveAndFlush(row);
        return user;
    }

    User dependent(String realName) {
        User user = user(UserType.dependente, realName);
        Dependent row = new Dependent();
        row.setNickname(plad.TestNicknames.unique());
        row.setUser(user);
        dependentRepository.saveAndFlush(row);
        return user;
    }

    User admin() {
        return user(UserType.admin, "Admin Geral");
    }

    String nickname(User dependent) {
        return jdbcTemplate.queryForObject("select nickname from dependents where user_id = ?", String.class,
                dependent.getId());
    }

    long meeting(User host, Instant start, Instant end, String status) {
        return jdbcTemplate.queryForObject("""
                        insert into meetings (psychologist_id, title, starts_at, ends_at, capacity, status)
                        values (?, 'Sala', ?, ?, 10, ?) returning id""",
                Long.class, host.getId(), utc(start), utc(end), status);
    }

    void register(long meetingId, User dependent) {
        jdbcTemplate.update("insert into meeting_registrations (meeting_id, dependent_id) values (?, ?)",
                meetingId, dependent.getId());
    }

    boolean isRegistered(long meetingId, User dependent) {
        return jdbcTemplate.queryForObject("select count(*) from meeting_registrations where meeting_id = ? "
                + "and dependent_id = ?", Integer.class, meetingId, dependent.getId()) > 0;
    }

    String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private User user(UserType type, String realName) {
        User user = new User();
        user.setRealName(realName);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.saveAndFlush(user);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
