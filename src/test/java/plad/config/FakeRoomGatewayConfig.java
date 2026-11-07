package plad.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import plad.services.room.FakeMeetingRoomGateway;

// The tests never call LiveKit: this fake wins over the real LiveKitMeetingRoomGateway wherever a MeetingRoomGateway is
// injected. It is imported by TestcontainersConfig, which every test that loads a Spring context already imports, so
// no test adds a context variant of its own (each distinct context keeps a Hikari pool open on the shared Postgres).
@TestConfiguration(proxyBeanMethods = false)
public class FakeRoomGatewayConfig {

    @Bean
    @Primary
    FakeMeetingRoomGateway fakeMeetingRoomGateway() {
        return new FakeMeetingRoomGateway();
    }
}
