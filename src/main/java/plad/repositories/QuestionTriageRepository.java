package plad.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import plad.entity.QuestionTriage;

import java.util.List;

public interface QuestionTriageRepository extends JpaRepository<QuestionTriage,Long> {
    List<QuestionTriage> findAllByOrderByDisplayOrderAsc();
}
