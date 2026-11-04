package plad.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import plad.config.TestUsers;
import plad.config.TestcontainersConfig;
import plad.entity.User;
import plad.entity.UserType;
import plad.exception.InvalidRefreshTokenException;
import plad.repositories.RefreshTokenRepository;
import plad.repositories.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Deliberately NOT @Transactional: the service owns its transaction here, so this proves that the revocation done
// on reuse is committed even though the same call ends in an exception (noRollbackFor on rotate).
@SpringBootTest
@Import(TestcontainersConfig.class)
class RefreshTokenReuseCommitTest {

    @Autowired
    private RefreshTokenService service;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private UserRepository users;

    private User user;

    @AfterEach
    void deleteUser() {
        if (user != null) {
            users.deleteById(user.getId()); // refresh_tokens go with it (ON DELETE CASCADE)
        }
    }

    @Test
    void revocationAfterReuseSurvivesTheExceptionThatReportsIt() {
        user = TestUsers.save(users, UserType.dependente);
        String first = service.start(user).refreshToken();
        String second = service.rotate(first).refreshToken();

        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokens.findByTokenHash(RefreshTokenService.hash(second)).orElseThrow().getRevokedAt())
                .as("the legitimate token issued by the first rotation").isNotNull();
        assertThatThrownBy(() -> service.rotate(second)).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
