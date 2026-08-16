CREATE TABLE story_audio (
    id                BIGSERIAL PRIMARY KEY,
    media_id          VARCHAR(255)  NOT NULL,
    title             VARCHAR(500)  NOT NULL,
    file_name         VARCHAR(255)  NOT NULL,
    file_path         VARCHAR(1000) NOT NULL,
    url               VARCHAR(1000) NOT NULL,
    voice_id          VARCHAR(100),
    model_id          VARCHAR(100),
    character_count   INTEGER,
    file_size         BIGINT,
    content_hash      VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_story_audio_media_id UNIQUE (media_id)
);

CREATE INDEX idx_story_audio_content_hash ON story_audio (content_hash);