package pladBack.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// MockMvc skips Tomcat, so these go through a real server; the peer is 127.0.0.1, a trusted proxy by default
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedClientIpTrustedProxyTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Test
    void clientsBehindATrustedProxyGetTheirOwnBuckets() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post("/auth/login", "203.0.113.10")).isEqualTo(400);
        }

        assertThat(post("/auth/login", "203.0.113.10")).isEqualTo(429);
        assertThat(post("/auth/login", "203.0.113.11")).isEqualTo(400);
    }

    @Test
    void addressesPrependedByTheClientDoNotChangeTheBucket() throws Exception {
        // the proxy appends the address it saw, so only the rightmost entry can be trusted
        for (int i = 0; i < 5; i++) {
            assertThat(post("/auth/register", "198.51.100." + i + ", 203.0.113.9")).isEqualTo(400);
        }

        assertThat(post("/auth/register", "198.51.100.99, 203.0.113.9")).isEqualTo(429);
    }

    private int post(String path, String forwardedFor) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
