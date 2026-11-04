package plad.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "dependents")
public class Dependent {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    // the anonymous name every other user sees. Changed only by NicknameService (native update), never by
    // flushing this entity, hence updatable = false
    @Column(name = "nickname", nullable = false, length = 60, updatable = false)
    private String nickname;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    //getters n setters


    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    //empty constructor
    public Dependent() {
    }

}
