-- Siste kjente utfall av saksbehandlingen av søknaden, sendt fra saksbehandling-api.
-- Saksbehandling-api melder når et vedtak er iverksatt, og når søknaden avbrytes eller gjenåpnes.
-- Null betyr at vi ikke har fått noe utfall ennå.
ALTER TABLE søknad ADD COLUMN behandlingsutfall varchar NULL;
-- Null for avbrudd og gjenåpning, som ikke har vedtak.
ALTER TABLE søknad ADD COLUMN behandlingsutfall_vedtak_id varchar NULL;
ALTER TABLE søknad ADD COLUMN behandlingsutfall_tidspunkt timestamp NULL;
