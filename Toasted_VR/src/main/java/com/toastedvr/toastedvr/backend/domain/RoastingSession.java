package com.toastedvr.toastedvr.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "roasting_sessions")
public class RoastingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Sin nullable = false: Hibernate agrega esta columna con ddl-auto
    // update, y un ALTER TABLE ADD COLUMN NOT NULL falla si la tabla ya
    // tiene filas (no hay valor para las existentes). La obligatoriedad
    // real para sesiones nuevas ya la exige @NotNull en SaveSessionRequest.
    @Column
    private Double chargeTemperature;

    @Column(nullable = false)
    private Double targetTemperature;

    @Column(nullable = false)
    private Integer totalDurationSeconds;

    @Column(nullable = false)
    private Double finalTemperature;

    @Column(nullable = false)
    private Double peakTemperature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoastingResult result;

    @Column(nullable = false)
    private Integer qualityScore;

    @Column(nullable = false)
    private Boolean firstCrackReached;

    private Integer developmentTimeSeconds;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RoastingSession() {
    }

    public RoastingSession(
        User user,
        Double chargeTemperature,
        Double targetTemperature,
        Integer totalDurationSeconds,
        Double finalTemperature,
        Double peakTemperature,
        RoastingResult result,
        Integer qualityScore,
        Boolean firstCrackReached,
        Integer developmentTimeSeconds
    ) {
        this.user = user;
        this.chargeTemperature = chargeTemperature;
        this.targetTemperature = targetTemperature;
        this.totalDurationSeconds = totalDurationSeconds;
        this.finalTemperature = finalTemperature;
        this.peakTemperature = peakTemperature;
        this.result = result;
        this.qualityScore = qualityScore;
        this.firstCrackReached = firstCrackReached;
        this.developmentTimeSeconds = developmentTimeSeconds;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Double getChargeTemperature() { return chargeTemperature; }
    public Double getTargetTemperature() { return targetTemperature; }
    public Integer getTotalDurationSeconds() { return totalDurationSeconds; }
    public Double getFinalTemperature() { return finalTemperature; }
    public Double getPeakTemperature() { return peakTemperature; }
    public RoastingResult getResult() { return result; }
    public Integer getQualityScore() { return qualityScore; }
    public Boolean isFirstCrackReached() { return firstCrackReached; }
    public Integer getDevelopmentTimeSeconds() { return developmentTimeSeconds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
