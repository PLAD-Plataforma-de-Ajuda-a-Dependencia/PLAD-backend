package pladBack.entity;


import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "psychologists")
public class Psychologist {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 150)
    private String graduate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "credential_type", nullable = false)
    private CrmCrpType credentialType;

    @Column(name = "crm_crp_number", nullable = false, unique = true, length = 30)
    private String crmCrpNumber;

    @Column(name ="is_validate", nullable = false)
    private boolean isValidate;

    @Column(name = "validate_time")
    private Instant validateTime;

    @PrePersist
    protected void onCreate() {
        this.isValidate = true;
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

    public String getGraduate() {
        return graduate;
    }

    public void setGraduate(String graduate) {
        this.graduate = graduate;
    }

    public CrmCrpType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CrmCrpType credentialType) {
        this.credentialType = credentialType;
    }

    public String getCrmCrpNumber() {
        return crmCrpNumber;
    }

    public void setCrmCrpNumber(String crmCrpNumber) {
        this.crmCrpNumber = crmCrpNumber;
    }

    public boolean isValidate() {
        return isValidate;
    }

    public void setValidate(boolean validate) {
        this.isValidate = isValidate;
    }

    public Instant getValidateTime() {
        return validateTime;
    }

    public void setValidateTime(Instant validateTime) {
        this.validateTime = validateTime;
    }

    //empty constructor
    public Psychologist() {
    }
}
