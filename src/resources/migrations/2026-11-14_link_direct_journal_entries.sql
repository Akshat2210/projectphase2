-- Run once in the existing application database before using this version.
-- New databases already include this column through structure.sql.
-- Historical rows remain unlinked; do not infer pairs from amounts or row IDs.
ALTER TABLE journalentry
    ADD COLUMN journal_group_id CHAR(36) DEFAULT NULL,
    ADD INDEX idx_journalentry_group (journal_group_id);
