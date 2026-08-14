CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- status/current_step/step_state per docs/architecture.md §4 and §14 — enum names
-- match domain.enums.{ProjectStatus,PipelineStep,StepState} exactly (uppercase).
-- Book text and generated images live on the local filesystem (spec §5.2) — this
-- table stores paths, not file bytes.
CREATE TABLE projects (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id),
    title             VARCHAR(255) NOT NULL,
    book_text_path    VARCHAR(1024) NOT NULL,
    style             TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'RUNNING', 'PAUSED', 'FAILED', 'COMPLETED')),
    current_step      VARCHAR(20)  NOT NULL DEFAULT 'STYLE'
                        CHECK (current_step IN ('STYLE', 'CHARACTERS', 'PORTRAITS', 'CHAPTERS', 'ILLUSTRATIONS')),
    step_state        VARCHAR(20)  NOT NULL DEFAULT 'IDLE'
                        CHECK (step_state IN ('IDLE', 'RUNNING', 'FAILED', 'COMPLETED')),
    step_started_at   TIMESTAMPTZ,
    last_error        TEXT,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_user_id ON projects (user_id);

-- Max 2 per project — enforced server-side in the application layer
-- (docs/architecture.md §19 / .claude/skills/pipeline-rules/SKILL.md §4), not a DB
-- constraint.
CREATE TABLE characters (
    id                   BIGSERIAL PRIMARY KEY,
    project_id           BIGINT       NOT NULL REFERENCES projects (id),
    name                 VARCHAR(255) NOT NULL,
    prompt               TEXT         NOT NULL,
    portrait_image_path  VARCHAR(1024),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_characters_project_id ON characters (project_id);

-- Max 1 per project — same enforcement note as characters.
CREATE TABLE chapters (
    id                        BIGSERIAL PRIMARY KEY,
    project_id                BIGINT       NOT NULL REFERENCES projects (id),
    name                      VARCHAR(255) NOT NULL,
    prompt                    TEXT         NOT NULL,
    illustration_image_path   VARCHAR(1024),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chapters_project_id ON chapters (project_id);
