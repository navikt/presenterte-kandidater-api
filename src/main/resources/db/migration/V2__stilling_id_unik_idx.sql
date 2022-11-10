DROP INDEX kandidatliste_stilling_id_idx;

CREATE UNIQUE INDEX kandidatliste_stilling_id_idx
ON kandidatliste(stilling_id);