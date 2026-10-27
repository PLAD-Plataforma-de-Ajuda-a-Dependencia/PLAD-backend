package pladBack.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pladBack.entity.QuestionTriage;

import java.util.List;

public interface questionTriageRepository extends JpaRepository<QuestionTriage,Long> {
    List<QuestionTriage> findAllByOrderByDisplayOrderAsc();
}
