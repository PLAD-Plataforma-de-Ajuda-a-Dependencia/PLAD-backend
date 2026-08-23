package pladBack.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pladBack.entity.ResultQuestionTriage;

import java.util.List;

public interface resultQuestionTriageRepository extends JpaRepository<ResultQuestionTriage,Long> {
    List<ResultQuestionTriage> findByTriageId(Long triageId);
}
