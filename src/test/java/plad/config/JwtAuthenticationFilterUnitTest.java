package plad.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.UserRepository;
import plad.services.JwtService;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// the token_version check without a database: the repository is a mock
class JwtAuthenticationFilterUnitTest {

    private static final String SECRET = "unit-test-secret-0123456789-0123456789-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);
    private final UserRepository users = mock(UserRepository.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtService, users, new SecurityErrorHandler(JsonMapper.builder().build()));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenWithTheCurrentVersionAuthenticates() throws Exception {
        when(users.findTokenVersionById(1L)).thenReturn(Optional.of(0));

        run(jwtService.generateToken(user(1L)));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    @Test
    void tokenWithAnOlderVersionIsIgnored() throws Exception {
        when(users.findTokenVersionById(1L)).thenReturn(Optional.of(1));

        run(jwtService.generateToken(user(1L)));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenOfAUserThatNoLongerExistsIsIgnored() throws Exception {
        when(users.findTokenVersionById(1L)).thenReturn(Optional.empty());

        run(jwtService.generateToken(user(1L)));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void databaseFailureAnswersServiceUnavailableWithoutCallingTheChain() throws Exception {
        when(users.findTokenVersionById(any())).thenThrow(new DataAccessResourceFailureException("db down"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        run(jwtService.generateToken(user(1L)), response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void requestWithoutABearerTokenNeverTouchesTheDatabase() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        verify(users, never()).findTokenVersionById(any());
        verify(chain).doFilter(any(), any());
    }

    private void run(String token) throws Exception {
        run(token, new MockHttpServletResponse());
    }

    private void run(String token, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, response, chain);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setTypeUser(UserType.dependente);
        return user;
    }
}
