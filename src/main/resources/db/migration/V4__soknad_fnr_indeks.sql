-- Duplikatsjekken ved innsending og visningen av tiltak slår opp brukerens søknader på fnr.
CREATE INDEX søknad_fnr_idx ON søknad (fnr);
