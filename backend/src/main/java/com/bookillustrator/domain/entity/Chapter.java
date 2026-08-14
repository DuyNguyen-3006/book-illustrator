package com.bookillustrator.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Max 1 per project — enforced in the application layer, not here. See pipeline-rules §4. */
@Entity
@Table(name = "chapters")
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String prompt;

    @Column(name = "illustration_image_path")
    private String illustrationImagePath;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Chapter() {
        // JPA
    }

    public Chapter(Long projectId, String name, String prompt) {
        this.projectId = projectId;
        this.name = name;
        this.prompt = prompt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getIllustrationImagePath() {
        return illustrationImagePath;
    }
}
