package plad;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import plad.config.TestcontainersConfig;

@SpringBootTest
@Import(TestcontainersConfig.class)
class PladApplicationTests {

	@Test
	void contextLoads() {
	}

}
