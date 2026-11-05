package plad.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import plad.entity.DependentNicknameHistory;

import java.util.List;

public interface DependentNicknameHistoryRepository extends JpaRepository<DependentNicknameHistory, Long> {

    List<DependentNicknameHistory> findByDependentIdOrderByAssignedAtAscIdAsc(Long dependentId);
}
