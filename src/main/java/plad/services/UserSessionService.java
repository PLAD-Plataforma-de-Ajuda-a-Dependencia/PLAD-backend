package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.repositories.UserRepository;

// Ends every session of a user: access tokens carry the token_version they were issued with and stop being accepted
// once it is bumped, and the refresh tokens are revoked so no new access token can be minted.
// Called by the password change (PasswordChangeService), in the same transaction as the change.
@Service
public class UserSessionService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;

    public UserSessionService(UserRepository users, RefreshTokenService refreshTokens) {
        this.users = users;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        users.incrementTokenVersion(userId);
        refreshTokens.revokeAll(userId);
    }
}
