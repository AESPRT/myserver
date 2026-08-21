ALTER TABLE story_audio
    ALTER COLUMN file_name DROP NOT NULL;

ALTER TABLE story_audio
    ALTER COLUMN file_path DROP NOT NULL;

ALTER TABLE story_audio
    ALTER COLUMN url DROP NOT NULL;

ALTER TABLE story_audio
    ALTER COLUMN file_size DROP NOT NULL;