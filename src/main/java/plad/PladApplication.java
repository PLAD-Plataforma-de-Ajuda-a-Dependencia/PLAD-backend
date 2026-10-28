package plad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// scheduling: RateLimiterService evicts idle buckets
@SpringBootApplication
@EnableScheduling
public class PladApplication {

	public static void main(String[] args) {
		SpringApplication.run(PladApplication.class, args);
	}

}
