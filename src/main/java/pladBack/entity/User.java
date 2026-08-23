package pladBack.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "real_name", nullable = false, length = 150)
    private String real_name;

    @Column(name = "password_hash", nullable = false)
    private String password_hash;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birth_date;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type_user", nullable = false)
    private userType typeUser;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", nullable = false)
    private sex gender;

    @Column(name = "created_at", updatable = false)
    private Instant created_at;

    @PrePersist
    protected void onCreate() {
        this.created_at = Instant.now();
    }

    //Getters e Setters


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReal_name() {
        return real_name;
    }

    public void setReal_name(String real_name) {
        this.real_name = real_name;
    }

    public String getPassword_hash() {
        return password_hash;
    }

    public void setPassword_hash(String password_hash) {
        this.password_hash = password_hash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getBirth_date() {
        return birth_date;
    }

    public void setBirth_date(LocalDate birth_date) {
        this.birth_date = birth_date;
    }

    public userType getTypeUser() {
        return typeUser;
    }

    public void setTypeUser(userType typeUser) {
        this.typeUser = typeUser;
    }

    public sex getGender() {
        return gender;
    }

    public void setGender(sex gender) {
        this.gender = gender;
    }

    public Instant getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Instant created_at) {
        this.created_at = created_at;
    }

    //Empty constructor
    public User() {

    }
}
