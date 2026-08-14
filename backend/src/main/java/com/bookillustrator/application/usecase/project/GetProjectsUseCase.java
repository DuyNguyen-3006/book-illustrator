package com.bookillustrator.application.usecase.project;

import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/** Spec §4.2: a user sees a list of their own projects, each with its current status. */
@Service
public class GetProjectsUseCase {

    private final ProjectRepository projectRepository;

    public GetProjectsUseCase(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<Result> execute(long userId) {
        return projectRepository.findByUserId(userId).stream()
                .map(GetProjectsUseCase::toResult)
                .toList();
    }

    private static Result toResult(Project project) {
        return new Result(
                project.getId(),
                project.getTitle(),
                project.getCreatedAt(),
                project.getStatus().name(),
                project.getCurrentStep().name(),
                project.getStepState().name());
    }

    public record Result(
            long projectId,
            String title,
            OffsetDateTime createdAt,
            String status,
            String currentStep,
            String stepState) {
    }
}
