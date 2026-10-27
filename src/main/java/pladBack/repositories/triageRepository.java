package pladBack.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pladBack.entity.Triage;

import java.util.Optional;

public interface triageRepository extends JpaRepository<Triage,Long> {
    // row lock (select ... for update) so concurrent resubmits of the same triage run one after the other
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Triage> findForUpdateByDependentUserId(Long dependentUserId);

    boolean existsByDependentUserId(Long dependentUserId);
}
