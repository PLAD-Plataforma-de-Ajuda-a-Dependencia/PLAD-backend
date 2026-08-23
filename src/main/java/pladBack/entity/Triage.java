package pladBack.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "triages")
public class Triage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "dependent_id", nullable = false, unique = true)
    private Dependent dependent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    //getters n setters


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dependent getDependent() {
        return dependent;
    }

    public void setDependent(Dependent dependent) {
        this.dependent = dependent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    //empty constructor
    public Triage() {}
}
