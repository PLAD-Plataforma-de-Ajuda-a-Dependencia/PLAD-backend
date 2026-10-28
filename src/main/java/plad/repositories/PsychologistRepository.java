package plad.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import plad.entity.Psychologist;

public interface PsychologistRepository extends JpaRepository<Psychologist,Long> {

    boolean existsByCrmCrpNumber(String crmCrpNumber);

}
