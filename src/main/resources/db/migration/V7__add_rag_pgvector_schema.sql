-- Enables the pgvector extension for storing and querying embedding
-- vectors directly in Postgres, avoiding a separate vector-DB service.
CREATE EXTENSION IF NOT EXISTS vector;

-- A named knowledge base a set of documents belongs to. Lets one API
-- back multiple independent chatbots (e.g. "paupahan-faq",
-- "foldgo-support") without their documents mixing in retrieval.
CREATE TABLE rag_collections (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(100) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rag_collections_slug UNIQUE (slug)
);

-- A source document before chunking. Kept separate from rag_chunks so
-- re-ingestion (e.g. a document was edited) can delete-and-replace all
-- chunks for one source_id without losing track of what the original
-- document was.
CREATE TABLE rag_documents (
    id            BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES rag_collections(id) ON DELETE CASCADE,
    external_id   VARCHAR(200),
    title         VARCHAR(500),
    source_type   VARCHAR(50) NOT NULL DEFAULT 'text',
    metadata      JSONB NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rag_documents_collection_id ON rag_documents (collection_id);
CREATE INDEX idx_rag_documents_external_id ON rag_documents (collection_id, external_id);

-- Individual embedded chunks. 1536 dimensions matches Cohere's
-- embed-v4.0 default output size -- if a different Cohere embedding
-- model/dimension is used later, this column width must change to match
-- (pgvector requires a fixed dimension per column).
CREATE TABLE rag_chunks (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
    collection_id BIGINT NOT NULL REFERENCES rag_collections(id) ON DELETE CASCADE,
    chunk_index   INT NOT NULL,
    content       TEXT NOT NULL,
    embedding     vector(1536) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rag_chunks_document_id ON rag_chunks (document_id);

-- IVFFlat index for approximate nearest-neighbor search, scoped per
-- collection via the WHERE-less composite approach (pgvector doesn't
-- support partial vector indexes well across arbitrary collections, so
-- collection_id is filtered at query time and this index accelerates
-- the vector distance search itself). Lists=100 is a reasonable default
-- for low-to-mid document volumes; revisit if a collection grows past
-- ~100k chunks.
CREATE INDEX idx_rag_chunks_embedding ON rag_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_rag_chunks_collection_id ON rag_chunks (collection_id);