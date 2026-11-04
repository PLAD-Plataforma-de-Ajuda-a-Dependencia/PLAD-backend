package plad.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import plad.services.nickname.NicknameGenerator;
import plad.services.nickname.NicknameWordLists;

import java.security.SecureRandom;

@Configuration
public class NicknameConfig {

    // SecureRandom so the next nickname cannot be guessed from the previous ones
    @Bean
    public NicknameGenerator nicknameGenerator() {
        return new NicknameGenerator(NicknameWordLists.load(), new SecureRandom());
    }
}
