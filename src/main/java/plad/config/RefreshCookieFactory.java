package plad.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// The refresh token travels only in this cookie: HttpOnly (no script can read it), SameSite=Strict (never sent on
// cross-site requests) and Path=/auth, so the browser sends it to /auth/refresh and /auth/logout and nowhere else.
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    static final String PATH = "/auth";

    private final boolean secure;

    // secure=false only for plain-http local development
    public RefreshCookieFactory(@Value("${auth.refresh-cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    public String issue(String refreshToken, Duration maxAge) {
        return build(refreshToken, maxAge).toString();
    }

    public String clear() {
        return build("", Duration.ZERO).toString();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
