package plad.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

// Rule (CLAUDE.md): a dependent's real name only travels in psychologist DTOs; everywhere else it is the nickname.
// The check is structural: any DTO with a real-name component has to be on the list below, so adding one is a
// conscious decision reviewed here, not an accident in a new response.
class RealNameExposureTest {

    // each entry says why it may carry a real name
    private static final Set<String> MAY_CARRY_A_REAL_NAME = Set.of(
            "RegisterRequestDTO",   // input: what the person typed when signing up
            "UserResponseDTO",      // answer to the registration: the person's own name, echoed back to them
            "MeResponseDTO",        // GET /me: only filled for psychologists; a dependent gets null
            "DependentIdentityDTO", // triage as read by a psychologist: the only place another person sees it
            "MeExportPsychologistDTO", // GET /me/export: a psychologist's own name, to that psychologist; the dependent block has none
            "MeetingParticipantResponseDTO", // GET /meetings/{id}/participants: the host psychologist only (decision 26), audited (27)
            "AdminPsychologistResponseDTO" // GET /admin/psychologists: a psychologist's name, to the admin only (decision 32); no e-mail, no dependent
    );

    @Test
    void onlyTheListedDtosHaveARealNameComponent() throws ClassNotFoundException {
        Set<String> withRealName = new TreeSet<>();
        for (Class<?> dto : dtoRecords()) {
            boolean hasRealName = Arrays.stream(dto.getRecordComponents())
                    .map(RecordComponent::getName)
                    .anyMatch(name -> name.toLowerCase().contains("realname") || name.toLowerCase().contains("real_name"));
            if (hasRealName) {
                withRealName.add(dto.getSimpleName());
            }
        }
        assertThat(withRealName).isSubsetOf(MAY_CARRY_A_REAL_NAME);
    }

    @Test
    void adminAndNicknameOnlyDtosNeverCarryOne() {
        for (Class<?> dto : List.of(AdminTriageReadResponseDTO.class, DependentNicknameDTO.class, NicknameResponseDTO.class,
                AccessLogEntryDTO.class, AuditLogResponseDTO.class, MeExportDependentDTO.class,
                MeExportNicknameDTO.class, MeExportTriageDTO.class, MeExportConsentDTO.class,
                MeExportMeetingRegistrationDTO.class,
                // the host's name goes out as `host.name` (decision 23, covered by MeetingControllerTest), never a
                // real_name/email key
                MeetingRequestDTO.class, MeetingResponseDTO.class, MeetingHostDTO.class)) {
            assertThat(Arrays.stream(dto.getRecordComponents()).map(RecordComponent::getName))
                    .as(dto.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().contains("realname"))
                    .noneMatch(name -> name.toLowerCase().contains("email"));
        }
    }

    // the admin's list of psychologists shows a psychologist's name (decision 32) but never an e-mail or anything of a dependent
    @Test
    void adminPsychologistListHasNoEmailAndNoDependentData() {
        assertThat(Arrays.stream(AdminPsychologistResponseDTO.class.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(name -> name.toLowerCase().contains("email"))
                .noneMatch(name -> name.toLowerCase().contains("nickname"))
                .noneMatch(name -> name.toLowerCase().contains("dependent"));
    }

    // the identity DTO is the psychologist's: the psychologist-side triage DTO is the only one that embeds it
    @Test
    void onlyThePsychologistTriageDtoEmbedsTheIdentity() throws ClassNotFoundException {
        for (Class<?> dto : dtoRecords()) {
            boolean embeds = Arrays.stream(dto.getRecordComponents()).anyMatch(c -> c.getType() == DependentIdentityDTO.class);
            if (embeds) {
                assertThat(dto).isEqualTo(TriageReadResponseDTO.class);
            }
        }
    }

    private static List<Class<?>> dtoRecords() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        List<Class<?>> records = new java.util.ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("plad.dto")) {
            records.add(Class.forName(definition.getBeanClassName()));
        }
        assertThat(records).isNotEmpty();
        return records;
    }
}
