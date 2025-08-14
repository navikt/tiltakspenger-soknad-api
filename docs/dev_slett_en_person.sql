-- Script for å slette en sak (person) fra tiltakspenger-soknad-api i dev
WITH søknadz AS (DELETE FROM søknad WHERE saksnummer = '202506031001' returning saksnummer)
SELECT saksnummer
FROM søknadz;
