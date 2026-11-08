package plad.services.room;

import org.springframework.stereotype.Component;
import plad.config.LiveKitProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

// The name a person carries in the video room: HMAC-SHA256 of the meeting id and the user id, keyed by
// MEETING_IDENTITY_SECRET, which LiveKit never sees. Opaque (it does not contain the user id and cannot be reversed
// without the key), stable for the same person in the same meeting (reconnecting keeps the identity) and different in
// every other meeting (the provider cannot follow someone from one meeting to the next).
@Component
public class MeetingIdentity {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN = "plad-meeting-identity:v1:";

    private final SecretKeySpec key;

    public MeetingIdentity(LiveKitProperties properties) {
        this.key = new SecretKeySpec(properties.identitySecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String of(long meetingId, long userId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM); // not thread-safe: one per call
            mac.init(key);
            byte[] digest = mac.doFinal((DOMAIN + meetingId + ":" + userId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    // constant time, so the comparison does not tell how much of a guessed identity was right
    public boolean matches(String candidate, long meetingId, long userId) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8),
                of(meetingId, userId).getBytes(StandardCharsets.UTF_8));
    }
}
