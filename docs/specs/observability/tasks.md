# Spec de observabilidad - Plan de tareas

## Objetivo

Definir el plan de trabajo para implementar observabilidad v1 en `documind-backend` usando Spec Driven Design.

Este documento no implementa codigo ni modifica Terraform. Solo convierte `requirements.md` y `design.md` en tareas ordenadas, verificables y pequenas.

El orden reduce riesgo: primero se consolida la documentacion, despues se implementa logging estructurado y `requestId`, y recien luego se avanza hacia tracing, Bedrock, metricas, AWS, dashboards, alertas y hardening.

## Fase 0: documentacion baseline

### TASK-DOC-001 - Confirmar baseline tecnico de observabilidad

- Objetivo: asegurar que el estado actual del proyecto este documentado antes de implementar observabilidad.
- Requerimientos relacionados: `REQ-CUR-001`, `REQ-CUR-002`, `REQ-CUR-003`, `REQ-CUR-004`.
- Archivos esperados:
  - `docs/project-technical-overview.md`
  - `docs/observability-backend-aws.md`
- Criterio de aceptacion: los documentos describen el endpoint `POST /questions`, Actuator, logs actuales en CloudWatch, integracion con Bedrock y limitaciones actuales.
- Forma de verificacion: revisar los documentos y confirmar que no describen capacidades futuras como si ya existieran.

### TASK-DOC-002 - Mantener requirements y design como fuente de verdad

- Objetivo: confirmar que la implementacion futura parta de `requirements.md` y `design.md`.
- Requerimientos relacionados: `REQ-DOC-001`.
- Archivos esperados:
  - `docs/specs/observability/requirements.md`
  - `docs/specs/observability/design.md`
  - `docs/specs/observability/tasks.md`
- Criterio de aceptacion: cada tarea futura referencia requerimientos estables y respeta el diseno tecnico aprobado.
- Forma de verificacion: revisar que cada tarea tenga IDs de requerimientos y que no aparezcan tareas fuera del alcance definido.

### TASK-DOC-003 - Documentar queries operativas iniciales

- Objetivo: preparar ejemplos de busqueda para diagnosticar requests por `requestId`, `traceId`, status y excepcion.
- Requerimientos relacionados: `REQ-DOC-001`, `REQ-AWS-001`.
- Archivos esperados:
  - `docs/specs/observability/queries.md`
- Criterio de aceptacion: existe una guia con queries de CloudWatch Logs Insights para casos comunes.
- Forma de verificacion: ejecutar las queries contra logs reales o de prueba cuando exista el formato JSON final.

## Fase 1: logging estructurado y requestId

Esta es la primera fase implementable. No incluye tracing completo.

### TASK-LOG-001 - Validar dependencia o configuracion para logs JSON

- Objetivo: elegir la forma compatible de emitir logs JSON en produccion.
- Requerimientos relacionados: `REQ-LOG-001`, `REQ-LOG-002`, `REQ-NFR-002`, `REQ-NFR-005`.
- Archivos esperados:
  - `pom.xml`
  - `src/main/resources/application.properties`
  - posible archivo de configuracion Logback, por ejemplo `src/main/resources/logback-spring.xml`
- Criterio de aceptacion: queda definida una configuracion compatible con Spring Boot `4.0.5` para logs JSON en produccion y formato legible en local si aplica.
- Forma de verificacion: correr build y arranque local; revisar que los logs productivos sean JSON parseable.

### TASK-LOG-002 - Definir campos finales del log principal

- Objetivo: cerrar el contrato de campos del evento principal de request.
- Requerimientos relacionados: `REQ-LOG-002`, `REQ-LOG-003`, `REQ-LOG-004`, `REQ-NFR-004`.
- Archivos esperados:
  - `docs/specs/observability/design.md`
  - configuracion de logging futura
  - clases futuras de logging/contexto si se crean
- Criterio de aceptacion: el evento principal incluye campos minimos como `timestamp`, `level`, `service`, `environment`, `requestId`, `traceId`, `spanId`, `method`, `path`, `status`, `outcome` y `latencyMs`.
- Forma de verificacion: ejecutar `POST /questions` y revisar el log emitido.

### TASK-LOG-003 - Crear mecanismo de requestId HTTP

- Objetivo: generar o reutilizar un `requestId` por cada request HTTP.
- Requerimientos relacionados: `REQ-CORR-001`, `REQ-CORR-002`, `REQ-LOG-002`.
- Archivos esperados:
  - clase nueva de filtro o interceptor HTTP en `src/main/java/...`
  - tests unitarios o de integracion relacionados
- Criterio de aceptacion: si la request no trae `X-Request-Id`, el backend genera uno; si trae uno valido, lo reutiliza.
- Forma de verificacion: ejecutar requests con y sin `X-Request-Id` y confirmar el valor en logs.

### TASK-LOG-004 - Devolver requestId en header de respuesta

- Objetivo: exponer el `requestId` efectivo al cliente mediante `X-Request-Id`.
- Requerimientos relacionados: `REQ-CORR-001`, `REQ-CORR-002`.
- Archivos esperados:
  - filtro o interceptor HTTP futuro
  - tests de integracion del endpoint `POST /questions`
- Criterio de aceptacion: toda respuesta de `POST /questions`, exitosa o fallida, incluye el header `X-Request-Id`.
- Forma de verificacion: llamar el endpoint con `curl -i` o test de integracion y revisar headers.

### TASK-LOG-005 - Agregar contexto de logging por request

- Objetivo: incluir `requestId`, metodo, path, ambiente y release en el contexto de logging.
- Requerimientos relacionados: `REQ-LOG-002`, `REQ-CORR-001`, `REQ-CORR-002`, `REQ-NFR-005`.
- Archivos esperados:
  - filtro o interceptor HTTP futuro
  - configuracion de logging futura
- Criterio de aceptacion: los logs emitidos durante una request contienen el mismo `requestId` y metadata basica consistente.
- Forma de verificacion: ejecutar una request y revisar logs asociados.

### TASK-LOG-006 - Emitir log principal de finalizacion de request

- Objetivo: registrar un unico evento operativo principal al finalizar cada request.
- Requerimientos relacionados: `REQ-LOG-002`, `REQ-LOG-003`, `REQ-NFR-004`.
- Archivos esperados:
  - filtro o interceptor HTTP futuro
  - tests de integracion del endpoint `POST /questions`
- Criterio de aceptacion: cada request a `POST /questions` genera un log principal con status, outcome y latencia.
- Forma de verificacion: ejecutar varias requests y contar logs principales por `requestId`.

### TASK-LOG-007 - Registrar errores con contexto minimo

- Objetivo: asegurar logs utiles para validacion, JSON malformado, `QuestionProcessingException` y errores inesperados.
- Requerimientos relacionados: `REQ-LOG-004`, `REQ-PRIV-001`, `REQ-PRIV-002`, `REQ-NFR-003`.
- Archivos esperados:
  - `GlobalExceptionHandler.java`
  - posibles helpers de sanitizacion
  - tests de errores HTTP
- Criterio de aceptacion: cada error tiene log con `requestId`, status, tipo de excepcion y mensaje sanitizado.
- Forma de verificacion: provocar request invalida, JSON malformado y error inesperado en ambiente de prueba.

### TASK-LOG-008 - Emitir evento de arranque con metadata

- Objetivo: registrar al iniciar el servicio la metadata operativa de ambiente y release.
- Requerimientos relacionados: `REQ-LOG-005`, `REQ-REL-001`.
- Archivos esperados:
  - clase futura de startup listener o componente equivalente
  - `src/main/resources/application.properties`
- Criterio de aceptacion: al arrancar, existe un log estructurado `application_started` con `service`, `environment`, `release`, region y perfil activo.
- Forma de verificacion: arrancar la aplicacion y buscar el evento en logs.

### TASK-LOG-009 - Probar privacidad de logs

- Objetivo: verificar que no se loggeen pregunta completa, respuesta completa, JWTs, secrets ni headers sensibles.
- Requerimientos relacionados: `REQ-PRIV-001`, `REQ-PRIV-002`, `REQ-PRIV-003`, `REQ-PRIV-004`, `REQ-NFR-003`.
- Archivos esperados:
  - tests automatizados de logging/sanitizacion
  - documentacion de prueba manual si aplica
- Criterio de aceptacion: valores sensibles ficticios no aparecen en logs.
- Forma de verificacion: ejecutar tests y pruebas manuales con valores unicos faciles de buscar.

## Fase 2: tracing HTTP y aplicacion

### TASK-TRACE-001 - Validar estrategia OpenTelemetry compatible

- Objetivo: decidir el mecanismo de tracing compatible con Spring Boot `4.0.5`, Java 21 y Spring Web MVC.
- Requerimientos relacionados: `REQ-TRACE-001`, `REQ-NFR-002`, `REQ-AWS-002`.
- Archivos esperados:
  - `pom.xml` si se usan dependencias
  - configuracion de runtime si se usa Java agent
  - documentacion de decision en `docs/specs/observability/design.md` si cambia
- Criterio de aceptacion: queda seleccionada una estrategia soportada para tracing HTTP y spans manuales.
- Forma de verificacion: revisar documentacion oficial y ejecutar una prueba minima de arranque.

### TASK-TRACE-002 - Instrumentar tracing HTTP entrante

- Objetivo: crear o continuar una traza para cada request a `POST /questions`.
- Requerimientos relacionados: `REQ-TRACE-001`, `REQ-TRACE-005`.
- Archivos esperados:
  - configuracion OpenTelemetry futura
  - `pom.xml` o configuracion del Java agent
  - tests o prueba manual documentada
- Criterio de aceptacion: una request genera un span HTTP con metodo, ruta y status.
- Forma de verificacion: ejecutar `POST /questions` y buscar la traza por `traceId`.

### TASK-TRACE-003 - Correlacionar logs con traceId y spanId

- Objetivo: incluir `traceId` y `spanId` reales del contexto OpenTelemetry en logs.
- Requerimientos relacionados: `REQ-LOG-002`, `REQ-TRACE-001`, `REQ-AWS-001`.
- Archivos esperados:
  - configuracion de logging futura
  - configuracion OpenTelemetry futura
- Criterio de aceptacion: el `traceId` del log permite abrir la traza correspondiente.
- Forma de verificacion: ejecutar una request, copiar `traceId` desde logs y buscarlo en tracing.

### TASK-TRACE-004 - Crear span manual del caso de uso

- Objetivo: representar `AskQuestionUseCase.execute(...)` como operacion de aplicacion.
- Requerimientos relacionados: `REQ-TRACE-002`, `REQ-TRACE-005`.
- Archivos esperados:
  - `AskQuestionUseCase.java`
  - posible helper de tracing
  - tests o prueba manual
- Criterio de aceptacion: la traza contiene un span estable para `ask_question` o `AskQuestionUseCase.execute`.
- Forma de verificacion: revisar una traza exitosa y confirmar el tramo del caso de uso.

### TASK-TRACE-005 - Marcar errores de aplicacion en spans

- Objetivo: reflejar errores de aplicacion en el span correcto.
- Requerimientos relacionados: `REQ-TRACE-004`, `REQ-LOG-004`.
- Archivos esperados:
  - `AskQuestionUseCase.java`
  - `GlobalExceptionHandler.java`
  - tests de error
- Criterio de aceptacion: un error inesperado aparece como error en la traza y mantiene correlacion con logs.
- Forma de verificacion: provocar un error controlado en ambiente de prueba y revisar logs/traza.

## Fase 3: tracing Bedrock

### TASK-BEDROCK-001 - Validar instrumentacion del AWS SDK v2

- Objetivo: confirmar si OpenTelemetry instrumenta automaticamente `BedrockAgentRuntimeClient` o si se necesita span manual.
- Requerimientos relacionados: `REQ-TRACE-003`, `REQ-NFR-002`.
- Archivos esperados:
  - `pom.xml` o configuracion del Java agent
  - nota de decision en documentacion si corresponde
- Criterio de aceptacion: queda definida la estrategia para observar `retrieveAndGenerate`.
- Forma de verificacion: revisar documentacion oficial y ejecutar una request integrada.

### TASK-BEDROCK-002 - Crear span de retrieveAndGenerate

- Objetivo: medir explicitamente la llamada a Bedrock.
- Requerimientos relacionados: `REQ-TRACE-003`, `REQ-TRACE-004`, `REQ-METRIC-004`.
- Archivos esperados:
  - `BedrockRagGatewayAdapter.java`
  - posible helper de tracing
  - tests unitarios con mock del cliente Bedrock si aplica
- Criterio de aceptacion: la traza muestra el tiempo consumido por `retrieveAndGenerate`.
- Forma de verificacion: ejecutar request en ambiente integrado y revisar el span `bedrock.retrieve_and_generate`.

### TASK-BEDROCK-003 - Agregar atributos seguros al span de Bedrock

- Objetivo: incluir metadata tecnica util sin exponer contenido sensible.
- Requerimientos relacionados: `REQ-TRACE-005`, `REQ-PRIV-005`.
- Archivos esperados:
  - `BedrockRagGatewayAdapter.java`
  - tests de privacidad si aplica
- Criterio de aceptacion: el span incluye region, operacion y metadata permitida; no incluye pregunta, respuesta, JWTs ni headers sensibles.
- Forma de verificacion: inspeccionar atributos de una traza exportada.

### TASK-BEDROCK-004 - Clasificar errores Bedrock/IAM/red/timeout

- Objetivo: distinguir categorias operativas de error alrededor de Bedrock.
- Requerimientos relacionados: `REQ-LOG-004`, `REQ-TRACE-004`, `REQ-ALERT-003`.
- Archivos esperados:
  - `BedrockRagGatewayAdapter.java`
  - posible enum/helper de clasificacion
  - tests unitarios de clasificacion
- Criterio de aceptacion: los errores conocidos se registran con categoria estable.
- Forma de verificacion: simular excepciones del AWS SDK y validar logs/spans.

### TASK-BEDROCK-005 - Registrar cantidad de citations sin contenido

- Objetivo: observar `citations_count` sin registrar snippets ni respuesta completa.
- Requerimientos relacionados: `REQ-METRIC-005`, `REQ-TRACE-005`, `REQ-PRIV-002`.
- Archivos esperados:
  - `BedrockRagGatewayAdapter.java`
  - `QuestionController.java` si el conteo se calcula al responder
  - tests unitarios
- Criterio de aceptacion: una respuesta exitosa permite observar la cantidad de citas sin contenido sensible.
- Forma de verificacion: ejecutar request exitosa y revisar log, span o metrica.

## Fase 4: metricas funcionales

### TASK-METRIC-001 - Validar mecanismo de metricas

- Objetivo: decidir si se usara Micrometer, OpenTelemetry Metrics o CloudWatch directo.
- Requerimientos relacionados: `REQ-METRIC-001`, `REQ-METRIC-002`, `REQ-METRIC-003`, `REQ-NFR-002`.
- Archivos esperados:
  - `pom.xml`
  - `src/main/resources/application.properties`
  - documentacion de decision si cambia el diseno
- Criterio de aceptacion: existe un mecanismo compatible para publicar metricas funcionales.
- Forma de verificacion: revisar documentacion oficial y ejecutar prueba minima local.

### TASK-METRIC-002 - Implementar contador de requests

- Objetivo: contar requests a `POST /questions`.
- Requerimientos relacionados: `REQ-METRIC-001`.
- Archivos esperados:
  - `QuestionController.java` o filtro/interceptor HTTP
  - tests de integracion
- Criterio de aceptacion: cada request incrementa el contador esperado.
- Forma de verificacion: ejecutar varias requests y confirmar incremento de la metrica.

### TASK-METRIC-003 - Implementar contador de errores

- Objetivo: medir errores por outcome, status o tipo controlado.
- Requerimientos relacionados: `REQ-METRIC-002`, `REQ-NFR-004`.
- Archivos esperados:
  - `GlobalExceptionHandler.java`
  - filtro/interceptor HTTP o componente de metricas
  - tests de errores
- Criterio de aceptacion: errores 4xx, 5xx y errores Bedrock se reflejan en metricas sin alta cardinalidad.
- Forma de verificacion: provocar errores conocidos y revisar series de metricas.

### TASK-METRIC-004 - Medir latencia total de POST /questions

- Objetivo: publicar latencia total del endpoint.
- Requerimientos relacionados: `REQ-METRIC-003`, `REQ-CUR-004`.
- Archivos esperados:
  - filtro/interceptor HTTP o instrumentacion de metricas
  - tests de integracion si aplica
- Criterio de aceptacion: se puede consultar p50 y p95 o histograma equivalente de latencia total.
- Forma de verificacion: generar trafico de prueba y revisar metricas.

### TASK-METRIC-005 - Medir latencia de Bedrock

- Objetivo: publicar latencia especifica de la llamada a Bedrock.
- Requerimientos relacionados: `REQ-METRIC-004`, `REQ-TRACE-003`.
- Archivos esperados:
  - `BedrockRagGatewayAdapter.java`
  - componente de metricas
  - tests con mock si aplica
- Criterio de aceptacion: se puede distinguir la latencia de Bedrock de la latencia total.
- Forma de verificacion: ejecutar request integrada y revisar metrica o span.

### TASK-METRIC-006 - Publicar citations_count

- Objetivo: medir cantidad de citas por respuesta sin registrar contenido.
- Requerimientos relacionados: `REQ-METRIC-005`, `REQ-PRIV-002`.
- Archivos esperados:
  - `BedrockRagGatewayAdapter.java`
  - componente de metricas
  - tests unitarios
- Criterio de aceptacion: la metrica o atributo reporta cantidad de citas sin snippets ni respuesta completa.
- Forma de verificacion: ejecutar request con respuesta mock/integrada y revisar valor observado.

## Fase 5: AWS/Terraform

### TASK-AWS-001 - Validar estrategia de exportacion de trazas

- Objetivo: decidir si las trazas salen directo a X-Ray o via ADOT Collector.
- Requerimientos relacionados: `REQ-AWS-002`, `REQ-NFR-002`.
- Archivos esperados:
  - documentacion de decision
  - archivos Terraform futuros si se implementa
  - configuracion futura de ECS/task definition
- Criterio de aceptacion: existe una decision documentada con permisos, variables y complejidad operacional.
- Forma de verificacion: revisar documentacion oficial y hacer prueba controlada en ambiente AWS.

### TASK-AWS-002 - Agregar variables de entorno de observabilidad en ECS

- Objetivo: inyectar environment, release y configuracion OTEL/logging al contenedor.
- Requerimientos relacionados: `REQ-NFR-005`, `REQ-REL-001`, `REQ-AWS-002`.
- Archivos esperados:
  - archivos en `infra/terraform`
  - `src/main/resources/application.properties` si aplica
- Criterio de aceptacion: la task ECS recibe variables como `APP_ENVIRONMENT`, `APP_RELEASE` y variables OTEL necesarias.
- Forma de verificacion: revisar task definition y logs de arranque.

### TASK-AWS-003 - Ajustar permisos IAM para telemetria

- Objetivo: permitir exportar trazas y metricas segun la estrategia elegida.
- Requerimientos relacionados: `REQ-AWS-002`, `REQ-ALERT-003`.
- Archivos esperados:
  - politicas IAM en `infra/terraform`
- Criterio de aceptacion: el task role tiene permisos minimos necesarios para la exportacion elegida.
- Forma de verificacion: desplegar en ambiente controlado y confirmar que no hay errores de permisos.

### TASK-AWS-004 - Revisar log group y retencion

- Objetivo: confirmar que CloudWatch Logs sigue siendo destino inicial y que la retencion es adecuada.
- Requerimientos relacionados: `REQ-CUR-003`, `REQ-AWS-001`, `REQ-NFR-004`.
- Archivos esperados:
  - `infra/terraform`
  - documentacion operativa si aplica
- Criterio de aceptacion: el log group y retencion estan definidos de forma intencional.
- Forma de verificacion: revisar Terraform plan y CloudWatch Logs.

### TASK-AWS-005 - Validar health/readiness usado por ALB

- Objetivo: resolver la diferencia entre el health check `/actuator/health/readiness` y la exposicion actual `health,info`.
- Requerimientos relacionados: `REQ-CUR-002`, `REQ-DASH-001`.
- Archivos esperados:
  - `src/main/resources/application.properties`
  - `infra/terraform`
- Criterio de aceptacion: el health check configurado responde correctamente o se ajusta la configuracion.
- Forma de verificacion: llamar el endpoint de health real en ambiente desplegado.

## Fase 6: dashboard y alertas

### TASK-DASH-001 - Crear dashboard minimo

- Objetivo: visualizar salud de `POST /questions` sin queries manuales.
- Requerimientos relacionados: `REQ-DASH-001`, `REQ-METRIC-001`, `REQ-METRIC-003`, `REQ-REL-001`.
- Archivos esperados:
  - Terraform futuro para dashboard o documentacion equivalente
- Criterio de aceptacion: el dashboard muestra requests por minuto, latencia p50/p95, error rate, errores Bedrock, health y release.
- Forma de verificacion: abrir dashboard y confirmar datos recientes o paneles correctamente configurados.

### TASK-ALERT-001 - Crear alerta de error rate

- Objetivo: alertar cuando la tasa de error de `POST /questions` supere un umbral.
- Requerimientos relacionados: `REQ-ALERT-001`, `REQ-METRIC-002`.
- Archivos esperados:
  - Terraform futuro para alarma
  - destino de notificacion si aplica
- Criterio de aceptacion: la alerta tiene umbral, ventana, severidad y destino definidos.
- Forma de verificacion: simular condicion o revisar evaluacion de alarma.

### TASK-ALERT-002 - Crear alerta de latencia p95

- Objetivo: alertar cuando p95 de `POST /questions` se mantenga alto.
- Requerimientos relacionados: `REQ-ALERT-002`, `REQ-METRIC-003`.
- Archivos esperados:
  - Terraform futuro para alarma
- Criterio de aceptacion: la alerta evalua p95 durante una ventana sostenida.
- Forma de verificacion: generar latencia controlada o validar configuracion.

### TASK-ALERT-003 - Crear alerta de errores Bedrock/IAM

- Objetivo: detectar aumento sostenido de errores en la dependencia externa critica.
- Requerimientos relacionados: `REQ-ALERT-003`, `REQ-METRIC-004`, `REQ-TRACE-004`.
- Archivos esperados:
  - Terraform futuro para alarma
  - metrica o filtro de logs para errores Bedrock/IAM
- Criterio de aceptacion: errores de Bedrock/IAM pueden alertar de forma diferenciada.
- Forma de verificacion: provocar falla controlada de configuracion o permisos en ambiente de prueba.

### TASK-ALERT-004 - Definir destino de notificaciones

- Objetivo: decidir donde llegan las alertas.
- Requerimientos relacionados: `REQ-ALERT-001`, `REQ-ALERT-002`, `REQ-ALERT-003`.
- Archivos esperados:
  - documentacion operativa
  - Terraform futuro si se usa SNS u otro destino
- Criterio de aceptacion: cada alerta tiene un destino claro y accionable.
- Forma de verificacion: disparar prueba de notificacion o revisar configuracion.

## Fase 7: hardening privacidad/costos

### TASK-HARD-001 - Auditar datos sensibles en logs

- Objetivo: comprobar que no se registren payloads, JWTs, secrets ni headers sensibles.
- Requerimientos relacionados: `REQ-PRIV-001`, `REQ-PRIV-002`, `REQ-PRIV-003`, `REQ-PRIV-004`, `REQ-NFR-003`.
- Archivos esperados:
  - tests automatizados
  - checklist operativo
- Criterio de aceptacion: pruebas con valores sensibles ficticios no encuentran esos valores en logs.
- Forma de verificacion: ejecutar pruebas y busquedas en CloudWatch Logs.

### TASK-HARD-002 - Auditar datos sensibles en spans

- Objetivo: comprobar que las trazas no tengan pregunta, respuesta, tokens ni headers sensibles.
- Requerimientos relacionados: `REQ-PRIV-005`, `REQ-NFR-003`.
- Archivos esperados:
  - tests o checklist de inspeccion de trazas
- Criterio de aceptacion: spans solo contienen atributos tecnicos permitidos.
- Forma de verificacion: revisar trazas exportadas con requests de prueba.

### TASK-HARD-003 - Revisar volumen de logs por request

- Objetivo: evitar costos y ruido por logs redundantes.
- Requerimientos relacionados: `REQ-NFR-004`, `REQ-LOG-003`.
- Archivos esperados:
  - documentacion de medicion
  - ajustes de logging si aplica
- Criterio de aceptacion: cada request genera una cantidad acotada de eventos, con un log principal claro.
- Forma de verificacion: ejecutar lote de requests y contar eventos por `requestId`.

### TASK-HARD-004 - Revisar sampling y volumen de trazas

- Objetivo: ajustar costo y utilidad de tracing si el trafico crece.
- Requerimientos relacionados: `REQ-NFR-001`, `REQ-NFR-004`, `REQ-AWS-002`.
- Archivos esperados:
  - configuracion OTEL futura
  - Terraform futuro si aplica
- Criterio de aceptacion: existe una estrategia de sampling o retencion acorde al volumen real.
- Forma de verificacion: medir cantidad de trazas generadas y costo aproximado.

### TASK-HARD-005 - Revisar cardinalidad de metricas

- Objetivo: asegurar que metricas y tags no usen valores de alta cardinalidad.
- Requerimientos relacionados: `REQ-NFR-004`, `REQ-METRIC-002`, `REQ-REL-001`.
- Archivos esperados:
  - componentes de metricas futuros
  - documentacion de metricas
- Criterio de aceptacion: no se usan `requestId`, `traceId`, pregunta, respuesta ni mensajes completos como tags.
- Forma de verificacion: inspeccionar series de metricas y tags generados.

### TASK-HARD-006 - Ejecutar suite manual de aceptacion v1

- Objetivo: validar la observabilidad completa contra los casos manuales definidos.
- Requerimientos relacionados: `MAN-001`, `MAN-002`, `MAN-003`, `MAN-004`, `MAN-005`, `MAN-006`, `MAN-007`.
- Archivos esperados:
  - checklist manual en documentacion
  - resultados de prueba si se documentan
- Criterio de aceptacion: request exitosa, request invalida, error Bedrock, error inesperado, deploy nuevo, busqueda por `requestId` y busqueda por `traceId` pasan.
- Forma de verificacion: ejecutar cada caso manual y guardar evidencia minima.

## Orden recomendado de implementacion

1. `TASK-DOC-001`
2. `TASK-DOC-002`
3. `TASK-LOG-001`
4. `TASK-LOG-002`
5. `TASK-LOG-003`
6. `TASK-LOG-004`
7. `TASK-LOG-005`
8. `TASK-LOG-006`
9. `TASK-LOG-007`
10. `TASK-LOG-008`
11. `TASK-LOG-009`
12. `TASK-TRACE-001`
13. `TASK-TRACE-002`
14. `TASK-TRACE-003`
15. `TASK-TRACE-004`
16. `TASK-TRACE-005`
17. `TASK-BEDROCK-001`
18. `TASK-BEDROCK-002`
19. `TASK-BEDROCK-003`
20. `TASK-BEDROCK-004`
21. `TASK-BEDROCK-005`
22. `TASK-METRIC-001`
23. `TASK-METRIC-002`
24. `TASK-METRIC-003`
25. `TASK-METRIC-004`
26. `TASK-METRIC-005`
27. `TASK-METRIC-006`
28. `TASK-AWS-001`
29. `TASK-AWS-002`
30. `TASK-AWS-003`
31. `TASK-AWS-004`
32. `TASK-AWS-005`
33. `TASK-DASH-001`
34. `TASK-ALERT-001`
35. `TASK-ALERT-002`
36. `TASK-ALERT-003`
37. `TASK-ALERT-004`
38. `TASK-DOC-003`
39. `TASK-HARD-001`
40. `TASK-HARD-002`
41. `TASK-HARD-003`
42. `TASK-HARD-004`
43. `TASK-HARD-005`
44. `TASK-HARD-006`

## Criterio de finalizacion de observabilidad v1

La iniciativa v1 se considera completa cuando:

- las fases 1 a 6 estan implementadas y verificadas;
- la fase 7 no encuentra exposicion de datos sensibles ni volumen excesivo;
- `POST /questions` puede diagnosticarse por `requestId` y `traceId`;
- una request exitosa muestra logs, trazas y metricas utiles;
- una request fallida muestra logs, trazas y metricas utiles;
- Bedrock queda diferenciado como dependencia externa;
- existe dashboard minimo;
- existen alertas minimas;
- metadata de release aparece en al menos una senal observable;
- la documentacion operativa permite buscar requests y errores sin conocimiento previo del sistema.
