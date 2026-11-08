package plad.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import plad.entity.Psychologist;

public interface PsychologistRepository extends JpaRepository<Psychologist,Long> {

    boolean existsByCrmCrpNumber(String crmCrpNumber);

    // the user comes with the page: the list shows the real name and the sign-up date, both on users
    @EntityGraph(attributePaths = "user")
    Page<Psychologist> findAllBy(Pageable pageable);

}
