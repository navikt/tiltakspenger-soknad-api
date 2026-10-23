-- Utfallet av saksbehandlingen av søknaden, sendt fra saksbehandling-api når vedtaket er iverksatt.
-- Null betyr at søknaden ikke er ferdig behandlet, eller at vi ikke har fått utfallet ennå.
ALTER TABLE søknad ADD COLUMN behandlingsutfall varchar NULL;
ALTER TABLE søknad ADD COLUMN behandlingsutfall_vedtak_id varchar NULL;
ALTER TABLE søknad ADD COLUMN behandlingsutfall_vedtakstidspunkt timestamp NULL;
