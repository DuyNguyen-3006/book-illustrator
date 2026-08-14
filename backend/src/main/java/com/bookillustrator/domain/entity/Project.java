package com.bookillustrator.domain.entity;

import com.bookillustrator.domain.enums.PipelineStep;
import com.bookillustrator.domain.enums.ProjectStatus;
import com.bookillustrator.domain.enums.StepState;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * A book-illustration project — the domain entity and the JPA-persisted row are the
 * same class per the user's explicit call, amending docs/architecture.md §4/§9.
 * See DECISIONS.md.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "book_text_path", nullable = false)
    private String bookTextPath;

    private String style;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private PipelineStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_state", nullable = false)
    private StepState stepState;

    @Column(name = "step_started_at")
    private OffsetDateTime stepStartedAt;

    @Column(name = "last_error")
    private String lastError;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Project() {
        // JPA
    }

    /** New project — matches the schema's own defaults (DRAFT / STYLE / IDLE). */
    public Project(Long userId, String title, String bookTextPath) {
        this.userId = userId;
        this.title = title;
        this.bookTextPath = bookTextPath;
        this.status = ProjectStatus.DRAFT;
        this.currentStep = PipelineStep.STYLE;
        this.stepState = StepState.IDLE;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        // pipeline-rules SKILL.md §1: resume and debugging both depend on this actually
        // reflecting the last state change, not just insert time.
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getBookTextPath() {
        return bookTextPath;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public PipelineStep getCurrentStep() {
        return currentStep;
    }

    public StepState getStepState() {
        return stepState;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getStyle() {
        return style;
    }

    // ---- Pipeline state machine — pipeline-rules SKILL.md §1 ----

    /** IDLE or FAILED -> RUNNING. Illegal while already RUNNING or once COMPLETED. */
    public void startStep() {
        if (stepState == StepState.RUNNING) {
            throw new IllegalPipelineStateException("step " + currentStep + " is already running");
        }
        if (stepState == StepState.COMPLETED) {
            throw new IllegalPipelineStateException(
                    "cannot restart step " + currentStep + " — it already completed");
        }
        this.stepState = StepState.RUNNING;
        this.status = ProjectStatus.RUNNING;
    }

    /** RUNNING -> COMPLETED. Illegal from any other step_state. */
    public void completeStep() {
        if (stepState != StepState.RUNNING) {
            throw new IllegalPipelineStateException(
                    "cannot complete step " + currentStep + " from state " + stepState);
        }
        this.stepState = StepState.COMPLETED;
    }

    /** RUNNING -> FAILED, records the error. Illegal from any other step_state. */
    public void failStep(String error) {
        if (stepState != StepState.RUNNING) {
            throw new IllegalPipelineStateException(
                    "cannot fail step " + currentStep + " from state " + stepState);
        }
        this.stepState = StepState.FAILED;
        this.lastError = error;
    }

    /**
     * Moves to the next pipeline step (resetting step_state to IDLE), or — from
     * ILLUSTRATIONS — marks the whole project COMPLETED. Only legal once the current
     * step has COMPLETED; this is what prevents skipping a step.
     */
    public void advanceToNextStep() {
        if (stepState != StepState.COMPLETED) {
            throw new IllegalPipelineStateException(
                    "cannot advance past step " + currentStep + " before it completes");
        }
        PipelineStep next = nextStep(currentStep);
        if (next == null) {
            this.status = ProjectStatus.COMPLETED;
            return;
        }
        this.currentStep = next;
        this.stepState = StepState.IDLE;
    }

    private static PipelineStep nextStep(PipelineStep step) {
        return switch (step) {
            case STYLE -> PipelineStep.CHARACTERS;
            case CHARACTERS -> PipelineStep.PORTRAITS;
            case PORTRAITS -> PipelineStep.CHAPTERS;
            case CHAPTERS -> PipelineStep.ILLUSTRATIONS;
            case ILLUSTRATIONS -> null;
        };
    }
}
