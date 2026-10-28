package plad.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import plad.entity.Dependent;

public interface DependentRepository extends JpaRepository<Dependent,Long> {
}
