package com.bookillustrator.domain.entity;

import com.bookillustrator.domain.enums.PipelineStep;
import com.bookillustrator.domain.enums.ProjectStatus;
import com.bookillustrator.domain.enums.StepState;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import org.junit.jupiter.api.Test;

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
