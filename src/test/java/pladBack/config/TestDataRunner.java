package pladBack.config;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import pladBack.entity.User;
import pladBack.entity.sex;
import pladBack.entity.userType;
import pladBack.repositories.userRepository;

import java.time.LocalDate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TestDataRunner {

    @Autowired
    private userRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        User user = new User();
        user.setReal_name("test");
        user.setEmail("test@example.com");
        user.setPassword_hash("test-hash");
        user.setBirth_date(LocalDate.of(2007, 1, 21));
        user.setGender(sex.masculino);
        user.setTypeUser(userType.dependente);

        userRepository.save(user);

        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
    }
}
