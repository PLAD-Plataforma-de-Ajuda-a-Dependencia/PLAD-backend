package plad.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import plad.dto.RegisterRequestDTO;
import plad.dto.UserResponseDTO;
import plad.entity.CrmCrpType;
import plad.entity.Psychologist;
import plad.entity.User;
import plad.entity.Sex;
import plad.entity.UserType;
import plad.exception.ConsentRequiredException;
import plad.exception.EmailAlreadyInUseException;
import plad.exception.InvalidRegistrationException;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NicknameService nicknameService;
    @Mock
    private PsychologistRepository psychologistRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ConsentService consentService;

    private static final String IP = "203.0.113.9";

    @InjectMocks
    private AuthService authService;

    @Test
    void registerChecksAndStoresNormalizedEmail() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        UserResponseDTO response = authService.register(dependentRequest("  Ana@Example.COM "), IP);

        verify(userRepository).existsByEmail("ana@example.com");
        assertThat(response.email()).isEqualTo("ana@example.com");
    }

    @Test
    void registerGivesTheDependentANicknameInTheSameFlow() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        authService.register(dependentRequest("ana@example.com"), IP);

        verify(nicknameService).assignInitial(7L);
    }

    @Test
    void registerGivesPsychologistsNoNickname() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        authService.register(psychologistRequest("06/12345"), IP);

        verifyNoInteractions(nicknameService);
    }

    @Test
    void registerTurnsConcurrentDuplicateEmailIntoConflict() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> authService.register(dependentRequest("ana@example.com"), IP))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verifyNoInteractions(nicknameService);
    }

    @Test
    void registerTurnsConcurrentDuplicateCredentialIntoBadRequest() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(psychologistRepository.saveAndFlush(any(Psychologist.class))).thenThrow(new DataIntegrityViolationException("crm_crp_number"));

        assertThatThrownBy(() -> authService.register(psychologistRequest("06/12345"), IP))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessageContaining("Credential number");
    }

    @Test
    void registerChecksAndStoresNormalizedCredentialNumber() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        authService.register(psychologistRequest(" 06 / 12345-sp "), IP);

        verify(psychologistRepository).existsByCrmCrpNumber("0612345SP");
        ArgumentCaptor<Psychologist> saved = ArgumentCaptor.forClass(Psychologist.class);
        verify(psychologistRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCrmCrpNumber()).isEqualTo("0612345SP");
    }

    @Test
    void registerRejectsPsychologistWithBlankCredentialNumber() {
        assertThatThrownBy(() -> authService.register(psychologistRequest("   "), IP))
                .isInstanceOf(InvalidRegistrationException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsAdminBeforeTouchingTheDatabase() {
        RegisterRequestDTO request = new RegisterRequestDTO("Admin", "admin@example.com", "senha-dev-123",
                LocalDate.of(1990, 1, 1), Sex.outro, UserType.admin, null, null, null, "1.0", "1.0");

        assertThatThrownBy(() -> authService.register(request, IP))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessageContaining("Admin");
        verifyNoInteractions(userRepository, nicknameService, psychologistRepository);
    }

    @Test
    void registerRefusesACrmPsychologistBeforeTouchingTheDatabase() {
        RegisterRequestDTO request = psychologistRequest(CrmCrpType.CRM, "06/12345");

        assertThatThrownBy(() -> authService.register(request, IP))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessage("CRM registrations are not accepted; register with a CRP");
        verifyNoInteractions(userRepository, nicknameService, psychologistRepository);
        verify(consentService, never()).record(any(), any());
    }

    @Test
    void registerIgnoresACredentialTypeSentByADependent() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        RegisterRequestDTO request = new RegisterRequestDTO("Ana Teste", "ana@example.com", "senha-dev-123",
                LocalDate.of(2000, 5, 20), Sex.nao_binario, UserType.dependente, CrmCrpType.CRM, null, null, "1.0", "1.0");

        authService.register(request, IP);

        verify(userRepository).saveAndFlush(any(User.class));
        verifyNoInteractions(psychologistRepository);
    }

    @Test
    void registerRecordsTheConsentOfTheNewUserWithTheClientIp() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        authService.register(dependentRequest("ana@example.com"), IP);

        verify(consentService).requireCurrent("1.0", "1.0");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(consentService).record(saved.capture(), eq(IP));
        assertThat(saved.getValue().getId()).isEqualTo(7L);
    }

    @Test
    void registerWithoutTheCurrentConsentTouchesNothing() {
        doThrow(new ConsentRequiredException("accept")).when(consentService).requireCurrent(any(), any());

        assertThatThrownBy(() -> authService.register(dependentRequest("ana@example.com"), IP))
                .isInstanceOf(ConsentRequiredException.class);
        verifyNoInteractions(userRepository, nicknameService, psychologistRepository);
        verify(consentService, never()).record(any(), any());
    }

    private static User withId(User user) {
        user.setId(7L);
        return user;
    }

    private static RegisterRequestDTO dependentRequest(String email) {
        return new RegisterRequestDTO("Ana Teste", email, "senha-dev-123", LocalDate.of(2000, 5, 20),
                Sex.nao_binario, UserType.dependente, null, null, null, "1.0", "1.0");
    }

    private static RegisterRequestDTO psychologistRequest(String crmCrpNumber) {
        return psychologistRequest(CrmCrpType.CRP, crmCrpNumber);
    }

    private static RegisterRequestDTO psychologistRequest(CrmCrpType credentialType, String crmCrpNumber) {
        return new RegisterRequestDTO("Psi Teste", "psi@example.com", "senha-dev-123", LocalDate.of(1985, 3, 10),
                Sex.feminino, UserType.psicologo, credentialType, crmCrpNumber, "Psicologia", "1.0", "1.0");
    }
}
