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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Calificación combinada de una sesión: la nota de la rúbrica de la IA y la nota final con los pesos
// de ese momento. Aparte de roasting_sessions para no tocar qualityScore. Se calcula una sola vez.
@Entity
@Table(name = "roast_evaluations")
public class RoastEvaluation {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RoastingSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoastEvaluationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RoastEvaluationSkipReason skipReason;

    // Null mientras está PENDING o si quedó FAILED; en NOT_APPLICABLE es el qualityScore.
    private Integer finalScore;

    private Double rubricScore;

    private Double rulesWeight;

    private Double aiWeight;

    private Boolean defectCapApplied;

    @Column(length = 60)
    private String rubricVersion;

    @Column(length = 100)
    private String model;

    // {"criteria": [...], "notApplicable": [...]} tal como lo devolvió el servicio IA.
    @Column(columnDefinition = "text")
    private String criteriaJson;

    @Column(nullable = false)
    private int attempts;

    private LocalDateTime nextAttemptAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    protected RoastEvaluation() {
    }

    private RoastEvaluation(RoastingSession session, RoastEvaluationStatus status) {
        this.session = session;
        this.status = status;
    }

    public static RoastEvaluation pending(RoastingSession session, LocalDateTime firstAttemptAt) {
        RoastEvaluation evaluation = new RoastEvaluation(session, RoastEvaluationStatus.PENDING);
        evaluation.nextAttemptAt = firstAttemptAt;
        return evaluation;
    }

    public static RoastEvaluation notApplicable(RoastingSession session, RoastEvaluationSkipReason reason, LocalDateTime now) {
        RoastEvaluation evaluation = new RoastEvaluation(session, RoastEvaluationStatus.PENDING);
        evaluation.skip(reason, null, null, now);
        return evaluation;
    }

    public void complete(CombinedScore score, String rubricVersion, String model, String criteriaJson, LocalDateTime now) {
        this.status = RoastEvaluationStatus.COMPLETED;
        this.rubricScore = score.rubricScore();
        this.finalScore = score.finalScore();
        this.rulesWeight = score.rulesWeight();
        this.aiWeight = score.aiWeight();
        this.defectCapApplied = score.defectCapApplied();
        this.rubricVersion = rubricVersion;
        this.model = model;
        this.criteriaJson = criteriaJson;
        finish(now);
    }

    public void skip(RoastEvaluationSkipReason reason, String rubricVersion, String criteriaJson, LocalDateTime now) {
        this.status = RoastEvaluationStatus.NOT_APPLICABLE;
        this.skipReason = reason;
        this.finalScore = session.getQualityScore();
        this.rubricVersion = rubricVersion;
        this.criteriaJson = criteriaJson;
        finish(now);
    }

    public void retryLater(String error, LocalDateTime nextAttemptAt) {
        this.attempts++;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void fail(String error, LocalDateTime now) {
        this.attempts++;
        this.status = RoastEvaluationStatus.FAILED;
        this.lastError = truncate(error);
        finish(now);
    }

    private void finish(LocalDateTime now) {
        this.nextAttemptAt = null;
        this.completedAt = now;
    }

    private static String truncate(String error) {
        return error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public RoastingSession getSession() { return session; }
    public RoastEvaluationStatus getStatus() { return status; }
    public RoastEvaluationSkipReason getSkipReason() { return skipReason; }
    public Integer getFinalScore() { return finalScore; }
    public Double getRubricScore() { return rubricScore; }
    public Double getRulesWeight() { return rulesWeight; }
    public Double getAiWeight() { return aiWeight; }
    public Boolean getDefectCapApplied() { return defectCapApplied; }
    public String getRubricVersion() { return rubricVersion; }
    public String getModel() { return model; }
    public String getCriteriaJson() { return criteriaJson; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
