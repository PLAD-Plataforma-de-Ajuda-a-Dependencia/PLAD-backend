package plad.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

// What the live room needs from the environment (LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET) plus the key of
// the opaque participant identities (MEETING_IDENTITY_SECRET). The last one is PLAD's alone: the video provider knows
// the API secret (it checks the token signature with it) but must never be able to turn an identity back into a user.
// The messages are fixed on purpose: they must never carry a configured value.
public record LiveKitProperties(String url, String apiKey, String apiSecret, String identitySecret) {

    static final int MIN_SECRET_BYTES = 32;
    // what .env.example ships ("troque-por-..."): long enough to pass the length check, so refused by name
    private static final String PLACEHOLDER_PREFIX = "troque-por-";

    public static LiveKitProperties validated(String url, String apiKey, String apiSecret, String identitySecret,
                                              boolean production) {
        requireUrl(url, production);
        if (apiKey == null || apiKey.isBlank() || isPlaceholder(apiKey)) {
            throw new IllegalStateException("LIVEKIT_API_KEY ausente ou com o valor de exemplo");
        }
        requireStrong(apiSecret, "LIVEKIT_API_SECRET");
        requireStrong(identitySecret, "MEETING_IDENTITY_SECRET");
        return new LiveKitProperties(url.strip(), apiKey.strip(), apiSecret, identitySecret);
    }

    // the browser connects to this URL (websocket); an https page cannot open ws:// (mixed content), so prod needs wss://
    private static void requireUrl(String url, boolean production) {
        String lower = url == null ? "" : url.strip().toLowerCase(Locale.ROOT);
        boolean secure = lower.startsWith("wss://") && lower.length() > "wss://".length();
        boolean plain = lower.startsWith("ws://") && lower.length() > "ws://".length();
        if (!(secure || (plain && !production))) {
            throw new IllegalStateException(production
                    ? "LIVEKIT_URL ausente ou fora do formato wss://host"
                    : "LIVEKIT_URL ausente ou fora do formato ws://host ou wss://host");
        }
    }

    private static void requireStrong(String secret, String name) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secret == null || secret.isBlank() || bytes.length < MIN_SECRET_BYTES || isPlaceholder(secret)) {
            throw new IllegalStateException(name + " ausente, com menos de 32 bytes ou com o valor de exemplo; "
                    + "gere com openssl rand -base64 48");
        }
    }

    private static boolean isPlaceholder(String value) {
        return value.strip().startsWith(PLACEHOLDER_PREFIX);
    }

    // a record would print the secrets in toString(): keep them out of any log line
    @Override
    public String toString() {
        return "LiveKitProperties[url=" + url + ", apiKey=***, apiSecret=***, identitySecret=***]";
    }
}
