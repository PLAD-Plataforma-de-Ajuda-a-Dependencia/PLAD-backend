package pladBack.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pladBack.entity.QuestionTriage;

public interface questionTriageRepository extends JpaRepository<QuestionTriage,Long> {
}
