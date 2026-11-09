# 0006 Respuestas sobre las bases: solo con citas literales comprobadas

- Estado: aceptada
- Fecha: 2026-11-09

## Contexto

«Pregunta a la convocatoria» deja preguntar en lenguaje normal sobre las bases de una
convocatoria («¿pueden pedirla los autónomos?»). La regla del proyecto es que no se inventa nada:
lo que no dice la convocatoria es «no consta». Un modelo de lenguaje, con los pasajes delante,
puede aun así completar con lo habitual, mezclar convocatorias o «citar» un texto que no está.

## Decisión

- **Recuperación (RAG):** el PDF de las bases se parte en pasajes que no cruzan de página; cada
  pasaje se guarda con su vector (embeddings de Gemini, 768 dimensiones) en PostgreSQL con
  **pgvector** e índice HNSW. Para cada pregunta se buscan los 6 pasajes más parecidos, solo de esa
  convocatoria.
- **Redacción:** Gemini responde con los pasajes numerados y devuelve, por cada dato, el número del
  pasaje y una cita literal (salida JSON con esquema, temperatura 0).
- **La comprobación es código, no confianza:** una cita solo vale si está tal cual en el pasaje que
  dice (sin que importen mayúsculas, saltos de línea o guiones de final de línea del PDF) y tiene
  al menos 15 caracteres. Si no queda ninguna cita válida, la respuesta es «no consta», diga lo que
  diga el modelo. La respuesta lleva la página de cada cita.
- **Indexado perezoso con ayuda:** cada convocatoria leída se apunta al llegar por Kafka; se
  indexan unas pocas cada hora y, si alguien pregunta por una sin indexar, se indexa en ese momento.
  Un PDF escaneado (sin texto) se marca y se dice que esas bases no se pueden consultar.
- **Evaluación con datos:** 20 preguntas reales sobre 4 convocatorias (más una escaneada), con la
  respuesta y la página comprobadas en el PDF. Se mide si acierta cuándo consta, si inventa, si cita
  la página buena y si la respuesta lleva el dato. En cada push se pasa con las respuestas de
  Gemini grabadas (sin clave ni cupo); un workflow manual la pasa contra Gemini de verdad.

## Consecuencias

- Una respuesta sin respaldo literal no llega nunca al usuario: en el peor caso, dice «no consta»
  cuando sí constaba. Es el error que se prefiere.
- Si un modelo cambia o se retira, la evaluación real lo detecta, y la grabada sigue fijando el
  comportamiento esperado del código.
- Las grabaciones se rehacen cuando cambian las instrucciones, el troceado o el conjunto de
  preguntas (la clave de cada grabación es la petición exacta).
