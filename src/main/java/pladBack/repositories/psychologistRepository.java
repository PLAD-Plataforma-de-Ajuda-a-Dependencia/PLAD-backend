package pladBack.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pladBack.entity.Psychologist;

import java.util.Optional;

public interface psychologistRepository extends JpaRepository<Psychologist,Long> {

    Optional<Psychologist> findByCrmCrpNumber(String crmCrpNumber);

}
