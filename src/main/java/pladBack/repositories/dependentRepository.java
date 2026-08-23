package pladBack.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pladBack.entity.Dependent;

public interface dependentRepository extends JpaRepository<Dependent,Long> {
}
