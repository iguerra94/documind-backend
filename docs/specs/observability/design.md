# Spec de observabilidad - Diseno tecnico

## Objetivo

Este documento define el diseno tecnico propuesto para implementar observabilidad v1 en `documind-backend`.

El objetivo es traducir los requerimientos de `docs/specs/observability/requirements.md` a decisiones tecnicas concretas, sin implementar codigo ni modificar Terraform todavia.

La idea central es que una request a `POST /questions` pueda observarse de punta a punta:

1. entra por HTTP;
2. recibe o genera un `requestId`;
3. queda asociada a un `traceId` y `spanId`;
4. ejecuta `AskQuestionUseCase`;
5. llama a Bedrock cuando corre con perfil `prod`;
6. genera logs estructurados;
7. publica metricas funcionales;
8. permite diagnosticar errores sin exponer datos sensibles.

Este diseno esta escrito para que un desarrollador junior pueda entender no solo que se propone, sino por que.

## Requerimientos cubiertos

Este diseno cubre los siguientes requerimientos:

- `REQ-CUR-001`: endpoint principal existente.
- `REQ-CUR-002`: health e info actuales.
- `REQ-CUR-003`: logs actuales en CloudWatch.
- `REQ-CUR-004`: latencia funcional actual en respuesta.
- `REQ-PRIV-001`: no loggear pregunta completa.
- `REQ-PRIV-002`: no loggear respuesta completa.
- `REQ-PRIV-003`: no loggear JWTs ni secrets.
- `REQ-PRIV-004`: no loggear headers sensibles.
- `REQ-PRIV-005`: no incluir contenido sensible en spans.
- `REQ-LOG-001`: logs JSON en produccion.
- `REQ-LOG-002`: campos minimos de log.
- `REQ-LOG-003`: log unico de finalizacion de request.
- `REQ-LOG-004`: logs de errores con contexto minimo.
- `REQ-LOG-005`: evento de arranque con metadata.
- `REQ-CORR-001`: generacion de `requestId`.
- `REQ-CORR-002`: reutilizacion de `requestId` entrante.
- `REQ-TRACE-001`: trace HTTP entrante.
- `REQ-TRACE-002`: trace del caso de uso.
- `REQ-TRACE-003`: trace de Bedrock.
- `REQ-TRACE-004`: errores marcados en spans.
- `REQ-TRACE-005`: atributos minimos de spans.
- `REQ-METRIC-001`: contador de requests.
- `REQ-METRIC-002`: contador de errores.
- `REQ-METRIC-003`: latencia total.
- `REQ-METRIC-004`: latencia de Bedrock.
- `REQ-METRIC-005`: cantidad de citas.
- `REQ-AWS-001`: logs consultables en CloudWatch.
- `REQ-AWS-002`: trazas exportadas a AWS.
- `REQ-DASH-001`: dashboard minimo.
- `REQ-ALERT-001`: alerta de error rate.
- `REQ-ALERT-002`: alerta de latencia p95.
- `REQ-ALERT-003`: alerta de errores Bedrock o IAM.
- `REQ-REL-001`: metadata de release.
- `REQ-DOC-001`: queries operativas documentadas.
- `REQ-NFR-001`: bajo overhead.
- `REQ-NFR-002`: compatibilidad con Spring Boot `4.0.5`.
- `REQ-NFR-003`: bajo riesgo de exponer datos sensibles.
- `REQ-NFR-004`: volumen de logs controlado.
- `REQ-NFR-005`: configuracion por ambiente.

## Diseno de logging estructurado

### Decision LOG-DES-001 - Usar JSON logs en produccion

En produccion, la aplicacion debe emitir logs en formato JSON. En desarrollo local se puede mantener un formato de texto legible si eso facilita el debug.

Mapea a:

- `REQ-LOG-001`
- `REQ-AWS-001`
- `REQ-NFR-005`

Motivo:

CloudWatch Logs Insights trabaja mejor cuando cada evento tiene campos parseables. Un log JSON permite buscar por `requestId`, `traceId`, `status` o `exceptionType` sin depender de expresiones sobre texto libre.

Pendiente de validacion:

- Elegir la libreria/configuracion exacta para JSON logging compatible con Spring Boot `4.0.5`.
- Validar si conviene usar configuracion Logback directa o una dependencia especifica de encoder JSON.

### Decision LOG-DES-002 - Emitir un evento principal al finalizar cada request

Cada request a `POST /questions` debe emitir un log principal de finalizacion. Ese evento representa el resultado final de la request.

Campos propuestos:

```text
timestamp
level
service
environment
release
logger
thread
message
requestId
traceId
spanId
method
path
status
outcome
latencyMs
exceptionType
exceptionMessage
```

Mapea a:

- `REQ-LOG-002`
- `REQ-LOG-003`
- `REQ-LOG-004`
- `REQ-NFR-004`

Motivo:

Un evento unico por request baja el ruido y evita que el equipo tenga que reconstruir el resultado mirando muchos logs. Si la request falla, el mismo evento debe indicar el tipo de error.

Reglas:

- No registrar body del request.
- No registrar body de la response.
- No registrar headers sensibles.
- `exceptionMessage` debe ser sanitizado antes de registrarse.

### Decision LOG-DES-003 - Centralizar el contexto de logging

El contexto de logging debe completarse al inicio de la request y limpiarse al finalizar.

Campos de contexto:

```text
requestId
traceId
spanId
method
path
environment
release
```

Mapea a:

- `REQ-CORR-001`
- `REQ-CORR-002`
- `REQ-LOG-002`
- `REQ-TRACE-001`

Motivo:

Si cada clase arma sus campos manualmente, es facil olvidar informacion o generar nombres distintos. Un contexto comun hace que los logs del controller, handler de errores y adapter puedan correlacionarse.

Pendiente de validacion:

- Confirmar la forma recomendada de integrar MDC con trazas OpenTelemetry en Spring Boot `4.0.5`.

### Decision LOG-DES-004 - Loggear errores desde el manejo centralizado

Los errores de validacion, JSON malformado, `QuestionProcessingException` y errores genericos deben quedar registrados con contexto minimo desde el flujo centralizado de errores.

Mapea a:

- `REQ-LOG-004`
- `REQ-PRIV-001`
- `REQ-PRIV-002`
- `REQ-PRIV-003`
- `REQ-PRIV-004`
- `REQ-NFR-003`

Motivo:

El repo ya tiene `GlobalExceptionHandler`. Usar ese punto permite mantener respuestas HTTP consistentes y logs de error consistentes.

Notas de diseno:

- Para errores de validacion, registrar el tipo de error y status `400`, no el body completo.
- Para errores de Bedrock mapeados a `QuestionProcessingException`, registrar que el origen fue Bedrock si esa clasificacion esta disponible.
- Para errores genericos, registrar el tipo de excepcion y un mensaje sanitizado.

## Diseno de requestId

### Decision REQID-DES-001 - Usar `X-Request-Id` como header de correlacion

El backend debe aceptar `X-Request-Id` como header entrante. Si el header existe y es valido, se reutiliza. Si no existe o no es valido, el backend genera un nuevo valor.

Mapea a:

- `REQ-CORR-001`
- `REQ-CORR-002`
- `MAN-006`

Motivo:

`X-Request-Id` es un nombre comun y facil de usar desde clientes, herramientas manuales y futuros frontends. No reemplaza al `traceId`; lo complementa.

Reglas de validacion propuestas:

- longitud maxima razonable, por ejemplo 128 caracteres;
- permitir letras, numeros, guion y guion bajo;
- rechazar valores con saltos de linea o caracteres de control;
- si el valor entrante no pasa validacion, generar uno nuevo.

Pendiente de validacion:

- Confirmar si algun ALB, proxy o frontend futuro ya usa otro header de correlacion.

### Decision REQID-DES-002 - Devolver `X-Request-Id` en la response

El backend deberia devolver el `requestId` efectivo en el header `X-Request-Id`.

Mapea a:

- `REQ-CORR-001`
- `REQ-CORR-002`
- `MAN-006`

Motivo:

Si un usuario reporta un problema, el cliente puede mostrar o capturar ese identificador. Eso facilita buscar logs sin exponer payloads.

Pendiente de validacion:

- Confirmar si se quiere exponer este header en todos los ambientes o solo en ambientes productivos/controlados.

### Decision REQID-DES-003 - No incluir `requestId` en el body actual

La primera version no debe modificar el contrato JSON de `QuestionResponse` ni de `ApiErrorResponse` solo para agregar `requestId`.

Mapea a:

- `REQ-CUR-001`
- `REQ-NFR-001`

Motivo:

Agregarlo como header es menos invasivo que cambiar el body. Mantiene el contrato funcional actual de `POST /questions`.

## Diseno de correlacion traceId/spanId

### Decision CORR-DES-001 - Tomar `traceId` y `spanId` desde OpenTelemetry

`traceId` y `spanId` deben venir del contexto activo de OpenTelemetry. La aplicacion no debe inventar manualmente esos valores.

Mapea a:

- `REQ-TRACE-001`
- `REQ-TRACE-005`
- `REQ-LOG-002`

Motivo:

El `traceId` identifica la traza completa y el `spanId` identifica una operacion dentro de esa traza. Si se generan manualmente fuera de OpenTelemetry, los logs no van a coincidir con la herramienta de tracing.

Pendiente de validacion:

- Validar la forma compatible de inyectar `traceId` y `spanId` en MDC/logs usando dependencias compatibles con Spring Boot `4.0.5`.

### Decision CORR-DES-002 - Incluir `requestId`, `traceId` y `spanId` en logs

El log principal de request y los logs de error deben incluir los tres campos cuando esten disponibles.

Mapea a:

- `REQ-LOG-002`
- `REQ-LOG-004`
- `REQ-AWS-001`
- `MAN-007`

Motivo:

`requestId` ayuda en busquedas operativas simples. `traceId` conecta logs con tracing. `spanId` permite ubicar el tramo exacto cuando hay varios spans en una traza.

## Diseno de tracing HTTP

### Decision HTTP-TRACE-DES-001 - Instrumentar automaticamente requests HTTP entrantes

La request HTTP entrante debe crear o continuar una traza automaticamente.

Mapea a:

- `REQ-TRACE-001`
- `REQ-TRACE-005`
- `REQ-NFR-001`

Motivo:

El span HTTP es la raiz natural del flujo observado. Debe mostrar metodo, ruta, status y latencia general sin que el controller tenga que construir todo manualmente.

Atributos propuestos:

```text
service.name=documind-backend
http.request.method=POST
http.route=/questions
http.response.status_code=<status>
app.request_id=<requestId>
deployment.environment=<environment>
service.version=<release>
```

Pendiente de validacion:

- Validar nombres exactos de atributos segun convenciones semanticas vigentes de OpenTelemetry.
- Validar dependencia compatible con Spring Boot Web MVC en Spring Boot `4.0.5`.

### Decision HTTP-TRACE-DES-002 - No crear spans manuales para cada detalle HTTP

No se deben crear spans manuales para pasos de bajo valor, como mapear DTOs o convertir cada citation.

Mapea a:

- `REQ-NFR-001`
- `REQ-NFR-004`

Motivo:

Demasiados spans hacen mas dificil leer una traza y agregan overhead. En v1 conviene observar pocos puntos importantes.

## Diseno de tracing del caso de uso

### Decision USECASE-TRACE-DES-001 - Crear un span manual para `AskQuestionUseCase`

La ejecucion de `AskQuestionUseCase.execute(...)` debe tener un span manual llamado de forma estable, por ejemplo:

```text
AskQuestionUseCase.execute
```

o:

```text
app.ask_question
```

Mapea a:

- `REQ-TRACE-002`
- `REQ-TRACE-005`

Motivo:

El caso de uso representa la operacion de aplicacion. Aunque hoy sea delgado, es el punto correcto para medir el flujo de negocio sin acoplar observabilidad solo al controller.

Atributos propuestos:

```text
app.operation=ask_question
app.request_id=<requestId>
```

Reglas de privacidad:

- No agregar `question`.
- No agregar longitud exacta si se considera sensible para algun caso.
- Si se agrega metadata de la pregunta, debe ser no sensible y aprobada en diseno posterior.

### Decision USECASE-TRACE-DES-002 - Marcar errores del caso de uso

Si `AskQuestionUseCase` falla, su span debe marcarse con estado de error.

Mapea a:

- `REQ-TRACE-004`
- `MAN-004`

Motivo:

Esto permite distinguir una falla de aplicacion de una falla en Bedrock o en el transporte HTTP.

## Diseno de tracing de Bedrock

### Decision BEDROCK-TRACE-DES-001 - Crear un span especifico para la llamada a Bedrock

La llamada a `client.retrieveAndGenerate(request)` debe quedar representada con un span especifico.

Nombre propuesto:

```text
bedrock.retrieve_and_generate
```

Mapea a:

- `REQ-TRACE-003`
- `REQ-TRACE-004`
- `REQ-METRIC-004`

Motivo:

Bedrock es la dependencia externa critica. Si una request tarda mucho, el equipo necesita saber si el tiempo se fue en Bedrock o en codigo propio.

Atributos propuestos:

```text
aws.region=<region>
aws.service=bedrock-agent-runtime
aws.operation=RetrieveAndGenerate
bedrock.knowledge_base_id=<valor controlado>
bedrock.model_arn=<valor controlado>
app.operation=ask_question
```

Pendiente de validacion:

- Confirmar nombres exactos de atributos AWS/OpenTelemetry recomendados.
- Decidir si `bedrock.knowledge_base_id` y `bedrock.model_arn` se registran completos, truncados o normalizados.
- Validar si la instrumentacion automatica del AWS SDK cubre Bedrock Agent Runtime o si se requiere span manual adicional.

### Decision BEDROCK-TRACE-DES-002 - Separar errores de Bedrock por categoria operativa

Cuando Bedrock falle, la observabilidad debe intentar clasificar el error en categorias como:

```text
bedrock
iam
network
timeout
configuration
unknown
```

Mapea a:

- `REQ-LOG-004`
- `REQ-TRACE-004`
- `REQ-ALERT-003`

Motivo:

No todos los errores se investigan igual. Un error de IAM requiere revisar permisos; un timeout requiere revisar latencia, red o dependencia externa.

Pendiente de validacion:

- Revisar excepciones reales del AWS SDK para Bedrock Agent Runtime y definir reglas de clasificacion confiables.

### Decision BEDROCK-TRACE-DES-003 - Registrar cantidad de citas, no contenido

Cuando Bedrock responda correctamente, se debe registrar `citations_count` como atributo de span, log o metrica. No se deben registrar snippets ni contenido completo de citas.

Mapea a:

- `REQ-METRIC-005`
- `REQ-TRACE-005`
- `REQ-PRIV-002`
- `REQ-PRIV-005`

Motivo:

La cantidad de citas es util para detectar cambios de comportamiento RAG. El contenido de las citas puede ser sensible y no debe entrar en observabilidad v1.

## Diseno de metricas funcionales

### Decision METRIC-DES-001 - Medir pocas metricas de negocio en v1

La primera version debe publicar metricas funcionales enfocadas en `POST /questions`.

Metricas propuestas:

```text
documind.questions.requests.total
documind.questions.errors.total
documind.questions.latency
documind.bedrock.latency
documind.bedrock.errors.total
documind.questions.citations.count
```

Mapea a:

- `REQ-METRIC-001`
- `REQ-METRIC-002`
- `REQ-METRIC-003`
- `REQ-METRIC-004`
- `REQ-METRIC-005`
- `REQ-NFR-001`

Motivo:

Pocas metricas bien definidas son mas utiles que muchas metricas dificiles de interpretar.

Pendiente de validacion:

- Confirmar nombres finales segun la libreria de metricas usada.
- Confirmar si se exportan via Micrometer, OpenTelemetry metrics o CloudWatch directo.

### Decision METRIC-DES-002 - Usar tags de baja cardinalidad

Tags propuestos:

```text
endpoint=/questions
method=POST
outcome=success|error
status=<2xx|4xx|5xx o codigo concreto si se decide>
exception=<tipo_controlado>
environment=<environment>
release=<release>
```

Mapea a:

- `REQ-METRIC-002`
- `REQ-REL-001`
- `REQ-NFR-004`

Motivo:

Los tags con demasiados valores distintos aumentan costo y hacen mas dificil consultar metricas. No deben usarse valores como `requestId`, `traceId`, pregunta, respuesta o mensajes completos como tags.

### Decision METRIC-DES-003 - Latencia con percentiles para dashboard y alertas

La latencia total de `POST /questions` y la latencia de Bedrock deben permitir ver p50 y p95.

Mapea a:

- `REQ-METRIC-003`
- `REQ-METRIC-004`
- `REQ-DASH-001`
- `REQ-ALERT-002`

Motivo:

El promedio puede ocultar requests lentas. p95 muestra mejor la experiencia de usuarios afectados por latencia alta.

Pendiente de validacion:

- Confirmar soporte y costo de percentiles segun el mecanismo final de exportacion.

## Diseno de metadata de release

### Decision REL-DES-001 - Usar `APP_RELEASE` como identificador principal

Se propone agregar una variable `APP_RELEASE` que contenga el identificador de version desplegada.

Valores posibles:

```text
image tag
commit SHA
version de build
```

Mapea a:

- `REQ-REL-001`
- `REQ-LOG-005`
- `REQ-DASH-001`

Motivo:

El equipo necesita poder responder: "esta degradacion empezo con que version?". Un valor unico de release en logs, metricas y trazas permite filtrar.

Pendiente de validacion:

- Definir si `APP_RELEASE` sera `image_tag`, commit SHA o ambos.
- Definir como el pipeline de deploy inyectara ese valor.

### Decision REL-DES-002 - Emitir evento de arranque

Al iniciar, el servicio debe registrar un evento estructurado con metadata operativa.

Campos propuestos:

```text
event=application_started
service=documind-backend
environment=<environment>
release=<APP_RELEASE>
aws.region=<AWS_REGION>
spring.profiles.active=<perfil>
```

Mapea a:

- `REQ-LOG-005`
- `REQ-REL-001`
- `MAN-005`

Motivo:

El evento de arranque permite confirmar que una task nueva esta corriendo con la version y entorno esperados.

## Diseno de cambios AWS/Terraform

No se debe modificar Terraform como parte de esta spec. Esta seccion describe cambios esperados para una fase futura.

### Decision AWS-DES-001 - Mantener CloudWatch Logs como destino inicial de logs

Los logs estructurados deben seguir saliendo por stdout/stderr del contenedor y llegando a CloudWatch Logs mediante `awslogs`.

Mapea a:

- `REQ-CUR-003`
- `REQ-AWS-001`
- `REQ-NFR-001`

Motivo:

Terraform ya configura logs del contenedor hacia CloudWatch. No hace falta introducir una plataforma nueva para logs en v1.

Cambios Terraform esperados en fase futura:

- Verificar nombre del log group.
- Revisar retencion de 14 dias.
- Agregar o documentar queries de CloudWatch Logs Insights.

### Decision AWS-DES-002 - Exportar trazas a AWS X-Ray o via ADOT Collector

La estrategia objetivo es OpenTelemetry con exportacion hacia AWS. Hay dos opciones:

1. Exportacion directa a X-Ray.
2. Exportacion via ADOT Collector.

Mapea a:

- `REQ-AWS-002`
- `REQ-TRACE-001`
- `REQ-TRACE-003`

Motivo:

X-Ray es el destino natural en AWS para visualizar trazas. ADOT Collector puede dar mas control, pero agrega complejidad operacional.

Decision propuesta para v1:

- Preferir la opcion mas simple que sea compatible y soportada oficialmente con Spring Boot `4.0.5`, ECS Fargate y Java 21.

Pendiente de validacion:

- Revisar documentacion oficial actual de AWS/OpenTelemetry.
- Confirmar si conviene exporter directo o ADOT Collector sidecar.
- Confirmar permisos IAM exactos para el task role.

### Decision AWS-DES-003 - Dashboard y alertas se disenan despues de tener metricas finales

El dashboard y las alertas deben basarse en los nombres finales de metricas y tags.

Mapea a:

- `REQ-DASH-001`
- `REQ-ALERT-001`
- `REQ-ALERT-002`
- `REQ-ALERT-003`

Motivo:

Crear dashboards antes de cerrar nombres de metricas genera retrabajo. La spec define que deben existir; el diseno final de Terraform debe esperar a nombres y destinos confirmados.

Cambios Terraform esperados en fase futura:

- `aws_cloudwatch_dashboard` o mecanismo equivalente.
- Alarmas de error rate.
- Alarmas de p95.
- Alarmas de errores Bedrock/IAM.
- Posible destino de notificacion, por ejemplo SNS, pendiente de decision.

## Variables de entorno propuestas

Estas variables son propuestas de diseno. No deben agregarse todavia hasta validar dependencias y estrategia de exportacion.

| Variable | Proposito | Requerimientos | Estado |
| --- | --- | --- | --- |
| `APP_ENVIRONMENT` | Nombre del ambiente, por ejemplo `prod`, `staging`, `local` | `REQ-LOG-002`, `REQ-METRIC-002`, `REQ-NFR-005` | Pendiente |
| `APP_RELEASE` | Version desplegada, image tag o commit SHA | `REQ-REL-001`, `REQ-LOG-005` | Pendiente |
| `LOG_FORMAT` | Controlar `json` o `plain` por ambiente | `REQ-LOG-001`, `REQ-NFR-005` | Pendiente |
| `OBSERVABILITY_ENABLED` | Activar/desactivar observabilidad avanzada | `REQ-NFR-005` | Pendiente |
| `OTEL_SERVICE_NAME` | Nombre del servicio para OpenTelemetry | `REQ-TRACE-005` | Pendiente |
| `OTEL_RESOURCE_ATTRIBUTES` | Atributos como entorno y release | `REQ-TRACE-005`, `REQ-REL-001` | Pendiente |
| `OTEL_TRACES_EXPORTER` | Exporter de trazas | `REQ-AWS-002` | Pendiente |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Endpoint OTLP si se usa collector | `REQ-AWS-002` | Pendiente |
| `OTEL_METRICS_EXPORTER` | Exporter de metricas si aplica | `REQ-METRIC-001` | Pendiente |

Variables existentes que deben seguir usandose:

| Variable | Uso actual |
| --- | --- |
| `AWS_REGION` | Region de AWS y del cliente Bedrock |
| `BEDROCK_KNOWLEDGE_BASE_ID` | Knowledge base usada por Bedrock |
| `BEDROCK_MODEL_ARN` | Modelo usado por Bedrock |
| `SPRING_PROFILES_ACTIVE` | Perfil activo, actualmente `prod` en ECS |

## Dependencias propuestas

Esta seccion no cierra versiones definitivas. Toda dependencia debe validarse con documentacion oficial y compatibilidad actual antes de implementarse.

### DEP-DES-001 - OpenTelemetry para tracing

Propuesta:

- Usar OpenTelemetry para instrumentacion HTTP, spans manuales y exportacion de trazas.

Mapea a:

- `REQ-TRACE-001`
- `REQ-TRACE-002`
- `REQ-TRACE-003`
- `REQ-AWS-002`
- `REQ-NFR-002`

Pendiente de validacion:

- Compatibilidad con Spring Boot `4.0.5`.
- Compatibilidad con Java 21.
- Mecanismo recomendado: Java agent, starter/autoconfiguracion o SDK manual.
- Soporte actual para Spring Web MVC.
- Soporte actual para AWS SDK v2 y Bedrock Agent Runtime.

### DEP-DES-002 - Integracion de logs JSON

Propuesta:

- Usar configuracion Logback o encoder JSON compatible para producir logs JSON en produccion.

Mapea a:

- `REQ-LOG-001`
- `REQ-LOG-002`
- `REQ-NFR-002`

Pendiente de validacion:

- Libreria exacta y version compatible con Spring Boot `4.0.5`.
- Forma de incluir MDC con `requestId`, `traceId` y `spanId`.

### DEP-DES-003 - Micrometer u OpenTelemetry Metrics para metricas funcionales

Propuesta:

- Evaluar si las metricas funcionales se implementan con Micrometer, OpenTelemetry Metrics o integracion directa con CloudWatch.

Mapea a:

- `REQ-METRIC-001`
- `REQ-METRIC-002`
- `REQ-METRIC-003`
- `REQ-METRIC-004`
- `REQ-METRIC-005`
- `REQ-NFR-002`

Pendiente de validacion:

- Camino recomendado para Spring Boot `4.0.5`.
- Exportacion a CloudWatch y costo.
- Soporte de percentiles p50/p95.

## Riesgos tecnicos

### RISK-001 - Dependencias incompatibles con Spring Boot 4.0.5

Si se eligen dependencias no compatibles, la aplicacion puede no compilar, no arrancar o no instrumentar correctamente.

Mitigacion:

- Validar documentacion oficial antes de implementar.
- Hacer una prueba minima de arranque.
- Agregar implementacion por fases.

Requerimientos relacionados:

- `REQ-NFR-002`

### RISK-002 - Exposicion accidental de datos sensibles

El riesgo principal es registrar preguntas, respuestas, JWTs, secrets o headers sensibles.

Mitigacion:

- No loggear bodies.
- Usar allowlist de campos permitidos en logs y spans.
- Agregar tests automatizados para headers sensibles.

Requerimientos relacionados:

- `REQ-PRIV-001`
- `REQ-PRIV-002`
- `REQ-PRIV-003`
- `REQ-PRIV-004`
- `REQ-PRIV-005`
- `REQ-NFR-003`

### RISK-003 - Aumento de costo por logs, metricas o trazas

Logs duplicados, demasiados spans o tags de alta cardinalidad pueden aumentar costo.

Mitigacion:

- Un log principal por request.
- Pocos spans manuales.
- Tags de baja cardinalidad.
- No usar `requestId` ni `traceId` como tags de metricas.

Requerimientos relacionados:

- `REQ-NFR-001`
- `REQ-NFR-004`

### RISK-004 - Trazas incompletas de Bedrock

Puede ocurrir que la instrumentacion automatica del AWS SDK no cubra la llamada especifica a Bedrock Agent Runtime.

Mitigacion:

- Crear span manual alrededor de `retrieveAndGenerate`.
- Validar despues si la instrumentacion automatica agrega spans adicionales utiles.

Requerimientos relacionados:

- `REQ-TRACE-003`
- `REQ-METRIC-004`

### RISK-005 - Health check actual puede no coincidir con readiness

El overview tecnico indica que Terraform usa por default `/actuator/health/readiness`, pero `application.properties` solo expone `health,info` y no confirma probes especificas de readiness.

Mitigacion:

- Validar estado real del endpoint antes de usarlo en dashboard o alertas.
- No asumir readiness detallado hasta confirmarlo.

Requerimientos relacionados:

- `REQ-CUR-002`
- `REQ-DASH-001`

## Decisiones pendientes

- Validar dependencias oficiales compatibles para OpenTelemetry con Spring Boot `4.0.5`.
- Decidir entre Java agent, starter/autoconfiguracion o SDK manual para tracing.
- Decidir si la exportacion de trazas sera directa a X-Ray o via ADOT Collector.
- Validar permisos IAM exactos para exportar trazas desde ECS Fargate.
- Definir si las metricas se exportan con Micrometer, OpenTelemetry Metrics o CloudWatch directo.
- Cerrar nombres finales de metricas y tags.
- Definir umbrales iniciales de alertas con datos reales o pruebas de carga.
- Definir si `APP_RELEASE` sera image tag, commit SHA o ambos.
- Definir si `bedrock.knowledge_base_id` y `bedrock.model_arn` se registran completos, truncados o normalizados.
- Confirmar si se devuelve `X-Request-Id` en todos los ambientes.
- Definir destino de notificaciones de alertas.
- Documentar queries exactas de CloudWatch Logs Insights despues de cerrar el formato JSON final.

## Plan de migracion por fases

### Fase 1 - Logging estructurado y requestId

Objetivo:

- Tener logs JSON utiles y un `requestId` por request.

Incluye:

- generar o reutilizar `X-Request-Id`;
- incluir `requestId` en contexto de logging;
- emitir log principal de finalizacion de request;
- emitir logs de error con contexto minimo;
- emitir evento de arranque con metadata;
- no registrar bodies ni headers sensibles.

Requerimientos principales:

- `REQ-LOG-001`
- `REQ-LOG-002`
- `REQ-LOG-003`
- `REQ-LOG-004`
- `REQ-LOG-005`
- `REQ-CORR-001`
- `REQ-CORR-002`
- `REQ-PRIV-001`
- `REQ-PRIV-002`
- `REQ-PRIV-003`
- `REQ-PRIV-004`

### Fase 2 - Tracing HTTP y caso de uso

Objetivo:

- Poder ver la request HTTP y la ejecucion de `AskQuestionUseCase` en una traza.

Incluye:

- instrumentacion HTTP entrante;
- `traceId` y `spanId` en logs;
- span manual del caso de uso;
- marcado de errores en spans.

Requerimientos principales:

- `REQ-TRACE-001`
- `REQ-TRACE-002`
- `REQ-TRACE-004`
- `REQ-TRACE-005`
- `REQ-AWS-002`

### Fase 3 - Tracing y medicion de Bedrock

Objetivo:

- Identificar cuanto tarda Bedrock y si falla.

Incluye:

- span para `retrieveAndGenerate`;
- clasificacion inicial de errores Bedrock/IAM/red/timeout/configuracion;
- latencia de Bedrock;
- `citations_count` sin contenido sensible.

Requerimientos principales:

- `REQ-TRACE-003`
- `REQ-TRACE-004`
- `REQ-METRIC-004`
- `REQ-METRIC-005`
- `REQ-ALERT-003`

### Fase 4 - Metricas funcionales

Objetivo:

- Medir throughput, errores y latencia de `POST /questions`.

Incluye:

- contador de requests;
- contador de errores;
- latencia total;
- latencia Bedrock;
- tags de baja cardinalidad;
- metadata de environment y release.

Requerimientos principales:

- `REQ-METRIC-001`
- `REQ-METRIC-002`
- `REQ-METRIC-003`
- `REQ-METRIC-004`
- `REQ-METRIC-005`
- `REQ-REL-001`

### Fase 5 - Dashboard y alertas

Objetivo:

- Hacer que las senales sean consumibles por operacion.

Incluye:

- dashboard minimo;
- alerta de error rate;
- alerta de p95;
- alerta de errores Bedrock/IAM;
- referencia visible de release/deploy.

Requerimientos principales:

- `REQ-DASH-001`
- `REQ-ALERT-001`
- `REQ-ALERT-002`
- `REQ-ALERT-003`
- `REQ-REL-001`

### Fase 6 - Hardening de privacidad y costo

Objetivo:

- Reducir riesgo operativo despues de la primera implementacion.

Incluye:

- revisar volumen real de logs;
- ajustar sampling de trazas si hace falta;
- revisar retencion;
- verificar que no haya datos sensibles en logs o spans;
- documentar queries operativas.

Requerimientos principales:

- `REQ-NFR-001`
- `REQ-NFR-003`
- `REQ-NFR-004`
- `REQ-DOC-001`
