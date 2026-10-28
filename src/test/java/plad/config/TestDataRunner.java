package plad.config;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import plad.entity.User;
import plad.entity.Sex;
import plad.entity.UserType;
import plad.repositories.UserRepository;

import java.time.LocalDate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TestDataRunner {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        User user = new User();
        user.setRealName("test");
        user.setEmail("test@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2007, 1, 21));
        user.setGender(Sex.masculino);
        user.setTypeUser(UserType.dependente);

        userRepository.save(user);

        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
    }
}
