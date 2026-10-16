package pladBack.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pladBack.entity.Psychologist;

public interface psychologistRepository extends JpaRepository<Psychologist,Long> {

    boolean existsByCrmCrpNumber(String crmCrpNumber);

}
