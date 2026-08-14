-- Per-project Gemini chaining state (pipeline-rules SKILL.md §5): the book is uploaded
-- to Gemini's File API once, and every subsequent text call chains off the last
-- interaction id instead of resending the book. Both nullable — absent until the
-- Style step (the project's first Gemini call) actually runs.
ALTER TABLE projects
    ADD COLUMN gemini_book_file_uri   VARCHAR(1024),
    ADD COLUMN last_text_interaction_id VARCHAR(255);
