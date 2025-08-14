Kan brukes i dev-miljøet dersom man ønsker å se at vedleggene er korrekte.

```
WITH numbered_attachments AS (
SELECT
id,
fnr,
jsonb_array_elements(vedlegg) as attachment,
row_number() OVER () as ordinality
FROM søknad where journalført is null
),
decoded_files AS (
SELECT
id,
fnr,
(attachment->>'filnavn') as filename,
decode(
(attachment->>'dokument'),
'base64'
)::bytea as file_content
FROM numbered_attachments
)
select
fnr,
filename,
file_content
FROM decoded_files;
```

I DBeaver kan man høyre klikke på en kolonne og velge "export data". Format settings -> general -> binaries -> save to files.
I IntelliJ kan man bare høyre klikke og trykke save to file eller lignende.
