-- El contexto de la traza (W3C traceparent) en la que se anotó el evento, para continuarla al publicarlo
ALTER TABLE outbox ADD COLUMN traza TEXT;
