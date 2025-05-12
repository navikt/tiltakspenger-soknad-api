SELECT COUNT(*)
FROM søknad
WHERE
  opprettet >= '2025-05-01'::date AND
  opprettet <= '2025-05-31'::date AND
  (
       (søknadspm->'kvalifiseringsprogram'->>'deltar') = 'true'
    OR (søknadspm->'introduksjonsprogram'->>'deltar') = 'true'
    OR (søknadspm->'etterlønn'->>'mottar') = 'true'
    OR (søknadspm->'sykepenger'->>'mottar') = 'true'
    OR (søknadspm->'jobbsjansen'->>'mottar') = 'true'
    OR (søknadspm->'alderspensjon'->>'mottar') = 'true'
    OR (søknadspm->'pensjonsordning'->>'mottar') = 'true'
    OR (søknadspm->'gjenlevendepensjon'->>'mottar') = 'true'
    OR (søknadspm->'supplerendestønadover67'->>'mottar') = 'true'
    OR (søknadspm->'supplerendestønadflyktninger'->>'mottar') = 'true'
    OR (søknadspm->>'mottarAndreUtbetalinger') = 'true'
    OR (søknadspm->'institusjonsopphold'->>'borPåInstitusjon') = 'true'
  );



