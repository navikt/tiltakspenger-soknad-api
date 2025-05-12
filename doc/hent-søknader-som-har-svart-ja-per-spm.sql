WITH date_range AS (
    SELECT 
        '2025-04-01'::date AS from_date,
        '2025-04-30'::date AS to_date
),
counts AS (
    SELECT
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'kvalifiseringsprogram'->>'deltar') = 'true') AS kvp,
         
         (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'introduksjonsprogram'->>'deltar') = 'true') AS intro,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'etterlønn'->>'mottar') = 'true') AS etterlønn,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'sykepenger'->>'mottar') = 'true') AS sykepenger,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'jobbsjansen'->>'mottar') = 'true') AS jobbsjansen,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'alderspensjon'->>'mottar') = 'true') AS alderspensjon,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'pensjonsordning'->>'mottar') = 'true') AS pensjonsordning,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'gjenlevendepensjon'->>'mottar') = 'true') AS gjenlevende,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'supplerendestønadover67'->>'mottar') = 'true') AS su67,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'supplerendestønadflyktninger'->>'mottar') = 'true') AS suflyktning,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->>'mottarAndreUtbetalinger') = 'true') AS andreUtbetalinger,
         
        (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
         AND (søknadspm->'institusjonsopphold'->>'borPåInstitusjon') = 'true') AS inst,
         
         (SELECT COUNT(*) FROM søknad 
         CROSS JOIN date_range 
         WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date) as total,
         
         (SELECT COUNT(*) FROM søknad
         cross join date_range 
	     WHERE opprettet BETWEEN date_range.from_date AND date_range.to_date
	     AND (
	         (søknadspm->'kvalifiseringsprogram'->>'deltar') = 'true' OR 
	         (søknadspm->'introduksjonsprogram'->>'deltar') = 'true' OR 
	         (søknadspm->'etterlønn'->>'mottar') = 'true' OR 
	         (søknadspm->'sykepenger'->>'mottar') = 'true' OR 
	         (søknadspm->'jobbsjansen'->>'mottar') = 'true' OR 
	         (søknadspm->'alderspensjon'->>'mottar') = 'true' OR 
	         (søknadspm->'pensjonsordning'->>'mottar') = 'true' OR 
	         (søknadspm->'gjenlevendepensjon'->>'mottar') = 'true' OR 
	         (søknadspm->'supplerendestønadover67'->>'mottar') = 'true' OR 
	         (søknadspm->'supplerendestønadflyktninger'->>'mottar') = 'true' OR 
	         (søknadspm->>'mottarAndreUtbetalinger') = 'true' OR 
	         (søknadspm->'institusjonsopphold'->>'borPåInstitusjon') = 'true'
	     )) AS sum
       
)
SELECT unnest(array[
    'kvp: ' || kvp,
    'intro: ' || intro,
    'etterlønn: ' || etterlønn,
    'sykepenger: ' || sykepenger,
    'jobbsjansen: ' || jobbsjansen,
    'alderspensjon: ' || alderspensjon,
    'pensjonsordning: ' || pensjonsordning,
    'gjenlevende: ' || gjenlevende,
    'su67: ' || su67,
    'suflyktning: ' || suflyktning,
    'andreUtbetalinger: ' || andreUtbetalinger,
    'inst: ' || inst,
    'sum: ' || sum,
    'total: ' || total
]) AS result
FROM counts;
