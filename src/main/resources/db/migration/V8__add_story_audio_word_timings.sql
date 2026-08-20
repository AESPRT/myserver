ALTER TABLE story_audio
ADD COLUMN word_timings JSONB NOT NULL DEFAULT '[]'::jsonb;