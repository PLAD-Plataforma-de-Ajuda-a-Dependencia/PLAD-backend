package pladBack.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pladBack.DTO.RegisterRequestDTO;
import pladBack.DTO.UserResponseDTO;
import pladBack.entity.CrmCrpType;
import pladBack.entity.Psychologist;
import pladBack.entity.User;
import pladBack.entity.sex;
import pladBack.entity.userType;
import pladBack.exception.EmailAlreadyInUseException;
import pladBack.exception.InvalidRegistrationException;
import pladBack.repositories.dependentRepository;
import pladBack.repositories.psychologistRepository;
import pladBack.repositories.userRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private userRepository userRepository;
    @Mock
    private dependentRepository dependentRepository;
    @Mock
    private psychologistRepository psychologistRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerChecksAndStoresNormalizedEmail() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        UserResponseDTO response = authService.register(dependentRequest("  Ana@Example.COM "));

        verify(userRepository).existsByEmail("ana@example.com");
        assertThat(response.email()).isEqualTo("ana@example.com");
    }

    @Test
    void registerTurnsConcurrentDuplicateEmailIntoConflict() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> authService.register(dependentRequest("ana@example.com")))
                .isInstanceOf(EmailAlreadyInUseException.class);
        verifyNoInteractions(dependentRepository);
    }

    @Test
    void registerTurnsConcurrentDuplicateCredentialIntoBadRequest() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(psychologistRepository.saveAndFlush(any(Psychologist.class))).thenThrow(new DataIntegrityViolationException("crm_crp_number"));

        assertThatThrownBy(() -> authService.register(psychologistRequest("06/12345")))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessageContaining("Credential number");
    }

    @Test
    void registerRejectsPsychologistWithBlankCredentialNumber() {
        assertThatThrownBy(() -> authService.register(psychologistRequest("   ")))
                .isInstanceOf(InvalidRegistrationException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    private static User withId(User user) {
        user.setId(7L);
        return user;
    }

    private static RegisterRequestDTO dependentRequest(String email) {
        return new RegisterRequestDTO("Ana Teste", email, "senha-dev-123", LocalDate.of(2000, 5, 20),
                sex.nao_binario, userType.dependente, null, null, null);
    }

    private static RegisterRequestDTO psychologistRequest(String crmCrpNumber) {
        return new RegisterRequestDTO("Psi Teste", "psi@example.com", "senha-dev-123", LocalDate.of(1985, 3, 10),
                sex.feminino, userType.psicologo, CrmCrpType.CRP, crmCrpNumber, "Psicologia");
    }
}
