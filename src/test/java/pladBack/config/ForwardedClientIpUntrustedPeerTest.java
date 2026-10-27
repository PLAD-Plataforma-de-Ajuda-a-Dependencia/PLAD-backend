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

// the only trusted proxy is 192.0.2.1, so the test client at 127.0.0.1 is an ordinary (untrusted) peer
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=192.0.2.1/32")
class ForwardedClientIpUntrustedPeerTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Test
    void forwardedForFromAnUntrustedPeerIsIgnored() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post("203.0.113." + i)).isEqualTo(400);
        }

        // a different forged address would get a fresh bucket if the header were honored
        assertThat(post("203.0.113.99")).isEqualTo(429);
    }

    private int post(String forwardedFor) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
