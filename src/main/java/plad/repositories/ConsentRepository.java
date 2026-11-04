package plad.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import plad.entity.Consent;

import java.util.List;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    boolean existsByUserIdAndDocumentAndVersion(Long userId, String document, String version);

    List<Consent> findByUserIdOrderByIdAsc(Long userId);
}
