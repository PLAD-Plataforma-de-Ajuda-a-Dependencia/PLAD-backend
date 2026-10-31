package plad.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.ResultQuestionTriage;

import java.util.Collection;
import java.util.List;

public interface ResultQuestionTriageRepository extends JpaRepository<ResultQuestionTriage,Long> {
    List<ResultQuestionTriage> findByTriageId(Long triageId);

    // one query for all answers of a page of triages, with each question loaded in the same select
    @EntityGraph(attributePaths = "question")
    List<ResultQuestionTriage> findByTriageIdInOrderByIdAsc(Collection<Long> triageIds);

    // bulk delete runs immediately; a derived delete would be flushed after the re-inserts and break the unique constraint
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResultQuestionTriage r where r.triage.id = :triageId")
    void deleteByTriageId(@Param("triageId") Long triageId);
}
