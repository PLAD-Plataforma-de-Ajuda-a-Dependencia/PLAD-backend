package plad.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "consents")
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "document", nullable = false, length = 30, updatable = false)
    private String document;

    @Column(name = "version", nullable = false, length = 20, updatable = false)
    private String version;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "ip", length = 45, updatable = false)
    private String ip;

    @PrePersist
    public void prePersist() {
        this.acceptedAt = Instant.now();
    }

    //getters n setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    //empty constructor
    public Consent() {}
}
