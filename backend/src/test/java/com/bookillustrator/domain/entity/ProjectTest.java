package com.bookillustrator.domain.entity;

import com.bookillustrator.domain.enums.PipelineStep;
import com.bookillustrator.domain.enums.ProjectStatus;
import com.bookillustrator.domain.enums.ResumeAction;
import com.bookillustrator.domain.enums.StepState;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain-only tests — no Spring, no DB. Covers pipeline-rules SKILL.md §1's legal and
 * illegal transitions directly (docs/architecture.md §18).
 */
class ProjectTest {

    private Project newProject() {
        return new Project(1L, "Title", "/book.txt");
    }

    @Test
    void newProjectStartsDraftStyleIdle() {
        Project project = newProject();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(project.getCurrentStep()).isEqualTo(PipelineStep.STYLE);
        assertThat(project.getStepState()).isEqualTo(StepState.IDLE);
    }

    @Test
    void startStepMovesIdleToRunningAndProjectToRunning() {
        Project project = newProject();

        project.startStep();

        assertThat(project.getStepState()).isEqualTo(StepState.RUNNING);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.RUNNING);
    }

    @Test
    void startStepIsIllegalWhileAlreadyRunning() {
        Project project = newProject();
        project.startStep();

        assertThatThrownBy(project::startStep).isInstanceOf(IllegalPipelineStateException.class);
    }

    @Test
    void startStepIsIllegalOnACompletedStep() {
        Project project = newProject();
        project.startStep();
        project.completeStep();

        assertThatThrownBy(project::startStep).isInstanceOf(IllegalPipelineStateException.class);
    }

    @Test
    void retryIsAllowedFromFailed() {
        Project project = newProject();
        project.startStep();
        project.failStep("gemini timed out");

        project.startStep();

        assertThat(project.getStepState()).isEqualTo(StepState.RUNNING);
    }

    @Test
    void completeStepRequiresRunning() {
        Project project = newProject();

        assertThatThrownBy(project::completeStep).isInstanceOf(IllegalPipelineStateException.class);
    }

    @Test
    void completeStepMovesRunningToCompleted() {
        Project project = newProject();
        project.startStep();

        project.completeStep();

        assertThat(project.getStepState()).isEqualTo(StepState.COMPLETED);
    }

    @Test
    void failStepRequiresRunningAndRecordsTheError() {
        Project project = newProject();

        assertThatThrownBy(() -> project.failStep("boom")).isInstanceOf(IllegalPipelineStateException.class);

        project.startStep();
        project.failStep("gemini timed out");

        assertThat(project.getStepState()).isEqualTo(StepState.FAILED);
    }

    @Test
    void advanceToNextStepRequiresCompleted() {
        Project project = newProject();
        project.startStep();

        assertThatThrownBy(project::advanceToNextStep).isInstanceOf(IllegalPipelineStateException.class);
    }

    @Test
    void advanceToNextStepMovesThroughAllFiveStepsThenCompletesTheProject() {
        Project project = newProject();
        PipelineStep[] order = {
                PipelineStep.STYLE, PipelineStep.CHARACTERS, PipelineStep.PORTRAITS,
                PipelineStep.CHAPTERS, PipelineStep.ILLUSTRATIONS
        };

        for (PipelineStep step : order) {
            assertThat(project.getCurrentStep()).isEqualTo(step);
            project.startStep();
            project.completeStep();
            project.advanceToNextStep();
        }

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(project.getCurrentStep()).isEqualTo(PipelineStep.ILLUSTRATIONS);
    }

    @Test
    void resumeActionIsStartWhileIdle() {
        Project project = newProject();

        assertThat(project.resumeAction(OffsetDateTime.now())).isEqualTo(ResumeAction.START);
    }

    @Test
    void resumeActionIsAdvanceOnceCompleted() {
        Project project = newProject();
        project.startStep();
        project.completeStep();

        assertThat(project.resumeAction(OffsetDateTime.now())).isEqualTo(ResumeAction.ADVANCE);
    }

    @Test
    void resumeActionIsSurfaceErrorWhileFailed() {
        Project project = newProject();
        project.startStep();
        project.failStep("gemini timed out");

        assertThat(project.resumeAction(OffsetDateTime.now())).isEqualTo(ResumeAction.SURFACE_ERROR);
    }

    @Test
    void resumeActionIsInProgressWhileRunningWithinTheTtl() {
        Project project = newProject();
        project.startStep();

        OffsetDateTime justAfterStarting = project.getStepStartedAt().plusSeconds(5);

        assertThat(project.resumeAction(justAfterStarting)).isEqualTo(ResumeAction.IN_PROGRESS);
    }

    /** The literal "kill mid-step, reload, resumes correctly" scenario from #13. */
    @Test
    void resumeActionIsReclaimAndRestartWhenTheRunningStepIsPastTheTtl() {
        Project project = newProject();
        project.startStep();

        OffsetDateTime wellPastTheTtl = project.getStepStartedAt().plus(Project.LOCK_TTL).plusSeconds(1);

        assertThat(project.resumeAction(wellPastTheTtl)).isEqualTo(ResumeAction.RECLAIM_AND_RESTART);
    }

    // A RUNNING step with a null step_started_at isn't reachable through this class's
    // own API anymore (startStep() always sets it) — only via a raw DB row that
    // predates that guarantee or was written outside the domain model. See
    // ProjectResumeTest.reloadingARunningStepWithNoStartedAtIsTreatedAsStale for that.

    @Test
    void cannotSkipAStepStateResetsToIdleOnAdvance() {
        Project project = newProject();
        project.startStep();
        project.completeStep();
        project.advanceToNextStep();

        assertThat(project.getCurrentStep()).isEqualTo(PipelineStep.CHARACTERS);
        assertThat(project.getStepState()).isEqualTo(StepState.IDLE);
        // Can't jump into CHARACTERS already running/completed — must start it properly.
        assertThatThrownBy(project::completeStep).isInstanceOf(IllegalPipelineStateException.class);
    }
}
