ALTER TABLE subscriptions
DROP COLUMN IF EXISTS jwt_token;

ALTER TABLE subscriptions
ADD COLUMN IF NOT EXISTS api_key_id VARCHAR(100);

ALTER TABLE subscriptions
ADD COLUMN IF NOT EXISTS api_key_hash TEXT;