package plad.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.Consent;

import java.time.Instant;
import java.util.List;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    // an acceptance counts only while it is not revoked (V27)
    boolean existsByUserIdAndDocumentAndVersionAndRevokedAtIsNull(Long userId, String document, String version);

    // The gate of the health data writes reads the acceptance with FOR SHARE: a revocation in flight (an UPDATE of this
    // row) waits for the write to commit and then deletes what it stored, and a write that comes after the revocation
    // finds no row. Both sides lock the consent first, so they cannot deadlock.
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Consent> findByUserIdAndDocumentAndVersionAndRevokedAtIsNull(Long userId, String document, String version);

    List<Consent> findByUserIdOrderByIdAsc(Long userId);

    // marks every acceptance of the document that is still in force, whatever its version; 0 when there is none, so
    // repeating it changes nothing. The rows stay as proof
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE consents SET revoked_at = :now "
            + "WHERE user_id = :userId AND document = :document AND revoked_at IS NULL", nativeQuery = true)
    int revokeActive(@Param("userId") Long userId, @Param("document") String document, @Param("now") Instant now);
}
