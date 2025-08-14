# Henting av antall svar på eøs-spørsmålet i søknaden. 

### Lister opp antall ja og nei på eøs-spørsmålet og sorterer de etter måned. Tar med både manuelle og registrerte barn fra pdl i summen.

```
SELECT
TO_CHAR(DATE_TRUNC('month', opprettet), 'YYYY-MM') AS mnd,
COUNT(*) FILTER (WHERE eøs = TRUE) AS antall_ja,
COUNT(*) FILTER (WHERE eøs = FALSE) AS antall_nei 
FROM (
    SELECT
    opprettet,
    (jsonb_array_elements(søknadspm->'barnetillegg'->'registrerteBarnSøktBarnetilleggFor')->>'oppholdInnenforEøs')::BOOLEAN AS eøs
    FROM søknad
    UNION ALL
    SELECT
    opprettet,
    (jsonb_array_elements(søknadspm->'barnetillegg'->'manueltRegistrerteBarnSøktBarnetilleggFor')->>'oppholdInnenforEøs')::BOOLEAN AS eøs
    FROM søknad
) extracted_data 
GROUP BY TO_CHAR(DATE_TRUNC('month', opprettet), 'YYYY-MM') -- Group by month and boolean value
ORDER BY mnd
```