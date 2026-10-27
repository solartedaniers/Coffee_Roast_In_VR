-- Runs automatically the first time the toasted_vr_postgres_data volume is
-- created (see docker-compose.yml). Spring AI's pgvector auto-config also
-- runs "CREATE EXTENSION IF NOT EXISTS vector" on startup when
-- spring.ai.vectorstore.pgvector.initialize-schema is enabled, so this file
-- is a belt-and-suspenders step, not the only place the extension is created.
CREATE EXTENSION IF NOT EXISTS vector;
