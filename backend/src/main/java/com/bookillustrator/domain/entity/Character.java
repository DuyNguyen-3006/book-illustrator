package com.bookillustrator.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Max 2 per project — enforced in the application layer, not here. See pipeline-rules §4. */
@Entity
@Table(name = "characters")
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String prompt;

    @Column(name = "portrait_image_path")
    private String portraitImagePath;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Character() {
        // JPA
    }

    public Character(Long projectId, String name, String prompt) {
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

    public String getPortraitImagePath() {
        return portraitImagePath;
    }
}
