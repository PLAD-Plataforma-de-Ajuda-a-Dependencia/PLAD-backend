package plad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

// Read-only view of dependent_nickname_history for the data export. Rows are written by DependentRepository's native
// queries (NicknameService), never by this entity.
@Entity
@Immutable
@Table(name = "dependent_nickname_history")
public class DependentNicknameHistory {

    @Id
    private Long id;

    @Column(name = "dependent_id", nullable = false)
    private Long dependentId;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    // null while the nickname is the one in use
    @Column(name = "replaced_at")
    private Instant replacedAt;

    protected DependentNicknameHistory() {
    }

    public Long getId() {
        return id;
    }

    public Long getDependentId() {
        return dependentId;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getReplacedAt() {
        return replacedAt;
    }
}
