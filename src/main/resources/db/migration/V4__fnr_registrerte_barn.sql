UPDATE søknad
SET søknad = jsonb_set(
        jsonb_set(
                søknad.søknad,
                '{spørsmålsbesvarelser,barnetillegg,registrerteBarnSøktBarnetilleggFor}',
                COALESCE(
                        (SELECT jsonb_agg(
                                        jsonb_set(
                                                elem,
                                                '{fnr}',
                                                'null'::jsonb
                                        )
                                )
                         FROM jsonb_array_elements(søknad.søknad -> 'spørsmålsbesvarelser' -> 'barnetillegg' ->
                                                   'registrerteBarnSøktBarnetilleggFor') AS elem),
                        '[]'::jsonb
                ),
                true
        ),
        '{spørsmålsbesvarelser,barnetillegg,manueltRegistrerteBarnSøktBarnetilleggFor}',
        COALESCE(
                (SELECT jsonb_agg(
                                jsonb_set(
                                        elem,
                                        '{fnr}',
                                        'null'::jsonb
                                )
                        )
                 FROM jsonb_array_elements(søknad.søknad -> 'spørsmålsbesvarelser' -> 'barnetillegg' ->
                                           'manueltRegistrerteBarnSøktBarnetilleggFor') AS elem),
                '[]'::jsonb
        ),
        true
             )
WHERE søknad -> 'spørsmålsbesvarelser' ? 'barnetillegg'
  AND (
    jsonb_array_length(søknad -> 'spørsmålsbesvarelser' -> 'barnetillegg' -> 'registrerteBarnSøktBarnetilleggFor') > 0
        OR jsonb_array_length(søknad -> 'spørsmålsbesvarelser' -> 'barnetillegg' ->
                              'manueltRegistrerteBarnSøktBarnetilleggFor') > 0
    );

UPDATE søknad
SET søknadspm = jsonb_set(
        jsonb_set(
                søknadspm,
                '{barnetillegg,registrerteBarnSøktBarnetilleggFor}',
                COALESCE(
                        (
                            SELECT jsonb_agg(
                                           jsonb_set(
                                                   elem,
                                                   '{fnr}',
                                                   'null'::jsonb
                                           )
                                   )
                            FROM jsonb_array_elements(søknadspm->'barnetillegg'->'registrerteBarnSøktBarnetilleggFor') AS elem
                        ),
                        '[]'::jsonb
                ),
                true
        ),
        '{barnetillegg,manueltRegistrerteBarnSøktBarnetilleggFor}',
        COALESCE(
                (
                    SELECT jsonb_agg(
                                   jsonb_set(
                                           elem,
                                           '{fnr}',
                                           'null'::jsonb
                                   )
                           )
                    FROM jsonb_array_elements(søknadspm->'barnetillegg'->'manueltRegistrerteBarnSøktBarnetilleggFor') AS elem
                ),
                '[]'::jsonb
        ),
        true
                   )
WHERE søknadspm ? 'barnetillegg'
  AND (
    jsonb_array_length(søknadspm->'barnetillegg'->'registrerteBarnSøktBarnetilleggFor') > 0
        OR jsonb_array_length(søknadspm->'barnetillegg'->'manueltRegistrerteBarnSøktBarnetilleggFor') > 0
    );