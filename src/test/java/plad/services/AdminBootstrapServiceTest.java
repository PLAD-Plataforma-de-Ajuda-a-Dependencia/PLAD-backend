package plad.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminBootstrapService service;

    @Test
    void missingEmailOrPasswordDoesNothing() {
        service.createIfAbsent(null, "senha-admin-123");
        service.createIfAbsent("", "senha-admin-123");
        service.createIfAbsent("admin@example.com", null);
        service.createIfAbsent("admin@example.com", "   ");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void existingEmailIsNeverTouched() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        service.createIfAbsent("admin@example.com", "senha-admin-123");

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createsAdminWithNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-admin-123")).thenReturn("hash");

        service.createIfAbsent("  Admin@Example.COM ", "senha-admin-123");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTypeUser()).isEqualTo(UserType.admin);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void shortPasswordStopsTheStartup() {
        assertThatThrownBy(() -> service.createIfAbsent("admin@example.com", "curta"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("curta");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordOverBcryptByteLimitStopsTheStartup() {
        assertThatThrownBy(() -> service.createIfAbsent("admin@example.com", "ç".repeat(40)))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordWithExactlyEightCharactersIsAccepted() {
        when(passwordEncoder.encode("a".repeat(8))).thenReturn("hash");

        service.createIfAbsent("admin@example.com", "a".repeat(8));

        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void passwordWithSevenCharactersIsRefused() {
        assertThatThrownBy(() -> service.createIfAbsent("admin@example.com", "a".repeat(7)))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordWithExactly72BytesIsAccepted() {
        when(passwordEncoder.encode("a".repeat(72))).thenReturn("hash");

        service.createIfAbsent("admin@example.com", "a".repeat(72));

        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void passwordWith73BytesIsRefused() {
        assertThatThrownBy(() -> service.createIfAbsent("admin@example.com", "a".repeat(73)))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    // the limit counts UTF-8 bytes, not characters: 36 "ç" are 36 characters but exactly 72 bytes
    @Test
    void multiByteLimitCountsBytesNotCharacters() {
        when(passwordEncoder.encode("ç".repeat(36))).thenReturn("hash");

        service.createIfAbsent("admin@example.com", "ç".repeat(36));
        verify(userRepository).saveAndFlush(any(User.class));

        assertThatThrownBy(() -> service.createIfAbsent("outro@example.com", "ç".repeat(37)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentCreationByAnotherInstanceIsNotAnError() {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatCode(() -> service.createIfAbsent("admin@example.com", "senha-admin-123")).doesNotThrowAnyException();
    }
}
