package plad.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plad.entity.Dependent;

public interface DependentRepository extends JpaRepository<Dependent,Long> {

    // Registration: creates the dependents row with its nickname. A nickname already taken is not an error, it
    // returns 0 so the caller draws another: a failed INSERT would abort the whole Postgres transaction.
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO dependents (user_id, nickname, created_at) VALUES (:userId, :nickname, now()) "
            + "ON CONFLICT (nickname) DO NOTHING", nativeQuery = true)
    int insertWithNickname(@Param("userId") Long userId, @Param("nickname") String nickname);

    // Reroll: 0 when the nickname is already taken. The unique constraint still guards the rare race between the
    // NOT EXISTS and the write. The persistence context is cleared so no stale entity keeps the old nickname.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE dependents SET nickname = :nickname WHERE user_id = :userId "
            + "AND NOT EXISTS (SELECT 1 FROM dependents WHERE nickname = :nickname)", nativeQuery = true)
    int updateNicknameIfFree(@Param("userId") Long userId, @Param("nickname") String nickname);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE dependent_nickname_history SET replaced_at = now() "
            + "WHERE dependent_id = :userId AND replaced_at IS NULL", nativeQuery = true)
    int closeCurrentNicknameHistory(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO dependent_nickname_history (dependent_id, nickname) VALUES (:userId, :nickname)",
            nativeQuery = true)
    int openNicknameHistory(@Param("userId") Long userId, @Param("nickname") String nickname);
}
