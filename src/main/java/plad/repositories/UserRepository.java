package plad.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // read by JwtAuthenticationFilter on every authenticated request: one column, no entity
    @Query("select u.tokenVersion from User u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);

    // atomic, so two concurrent bumps both count; the cached entity is dropped because it still holds the old value
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);

    // Account deletion. A bulk delete, not repository.delete(entity): the subtype, triage, consent, nickname history
    // and refresh token rows go with it through the ON DELETE CASCADE foreign keys (V24), which the persistence
    // context knows nothing about, so it is cleared afterwards.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from User u where u.id = :id")
    int deleteAccountById(@Param("id") Long id);
}
