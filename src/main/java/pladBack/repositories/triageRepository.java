package pladBack.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pladBack.entity.Triage;

import java.util.Optional;

public interface triageRepository extends JpaRepository<Triage,Long> {
    Optional<Triage> findByDependentUserId(Long dependentUserId);
}
