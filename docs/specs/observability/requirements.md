# Spec de observabilidad - Requerimientos

## Objetivo

Definir el comportamiento observable esperado para `documind-backend` antes de implementar cambios de codigo o infraestructura.

La observabilidad debe permitir entender que paso durante una request a `POST /questions`, cuanto tardo, si fallo, donde fallo y con que version desplegada ocurrio. El objetivo no es agregar logs o trazas por costumbre, sino hacer que el sistema sea operable en produccion sin exponer datos sensibles.

Esta spec usa Spec Driven Design: primero describe requerimientos verificables y despues, en una etapa posterior, se podra disenar e implementar la solucion.

## Baseline actual

Estado confirmado desde `docs/project-technical-overview.md`:

- El proyecto es un backend Spring Boot `4.0.5` con Java 21.
- El endpoint principal es `POST /questions`.
- El endpoint recibe un JSON con `question`, valida que no sea vacio y ejecuta `AskQuestionUseCase`.
- `AskQuestionUseCase` delega en el puerto `RagGateway`.
- En perfil `prod`, `BedrockRagGatewayAdapter` llama a AWS Bedrock Agent Runtime con `retrieveAndGenerate`.
- En perfil `mock`, `MockRagGatewayAdapter` devuelve una respuesta fija.
- El controller calcula `latencyMs` con `System.currentTimeMillis()` y lo devuelve en la respuesta exitosa.
- Existe manejo global de errores en `GlobalExceptionHandler`.
- Actuator esta instalado y se exponen `health,info`.
- La aplicacion corre en puerto `8080`.
- Terraform configura logs del contenedor hacia CloudWatch Logs con `awslogs`.
- Terraform crea el log group `/ecs/${var.project_name}` con retencion de 14 dias.

Limitaciones actuales confirmadas:

- No hay logging JSON estructurado confirmado en el repo.
- No hay `requestId` explicito por request.
- No hay `traceId` ni `spanId` explicitos.
- No hay OpenTelemetry.
- No hay tracing distribuido.
- No hay spans manuales en controller, caso de uso o Bedrock.
- No hay metricas funcionales de negocio para `POST /questions`.
- No hay dashboards confirmados.
- No hay alertas confirmadas.
- No hay logs especificos alrededor de la llamada a Bedrock.
- No hay timeout configurado explicitamente en el cliente Bedrock.
- No hay retry policy custom confirmada.

## Alcance

Esta iniciativa de observabilidad v1 incluye definir requerimientos para:

- logging estructurado;
- generacion y propagacion de `requestId`;
- presencia de `traceId` y `spanId` en logs y trazas;
- tracing HTTP para requests entrantes;
- tracing del caso de uso `AskQuestionUseCase`;
- tracing de la integracion con Bedrock;
- metricas funcionales del flujo `POST /questions`;
- dashboards operativos minimos;
- alertas operativas minimas;
- metadata de release visible en observabilidad.

## Fuera de alcance

No se hara en v1:

- tracing entre multiples repositorios o multiples servicios propios;
- logging de prompts completos;
- logging de preguntas completas;
- logging de respuestas completas;
- logging de JWTs;
- logging de secrets;
- dashboards avanzados de negocio;
- optimizacion profunda de costos;
- sistema centralizado multi-servicio;
- redisenar la arquitectura del flujo RAG;
- cambiar el contrato funcional de `POST /questions`;
- implementar nuevos endpoints de negocio;
- modificar codigo como parte de esta spec;
- modificar Terraform como parte de esta spec.

## Requisitos actuales

Estos requisitos describen capacidades ya confirmadas. Sirven como punto de partida y no implican trabajo nuevo.

### REQ-CUR-001 - Endpoint principal existente

- Descripcion: el sistema debe mantener como endpoint critico de observabilidad a `POST /questions`.
- Motivo: es el flujo principal de negocio confirmado en el repo.
- Criterio de aceptacion: la spec y los futuros disenos de observabilidad toman `POST /questions` como primer flujo a instrumentar.
- Forma de verificacion: revisar `QuestionController.java` y confirmar que expone `POST /questions`.

### REQ-CUR-002 - Health e info actuales

- Descripcion: el sistema cuenta actualmente con Actuator y expone `health,info`.
- Motivo: estos endpoints son la base operativa minima ya disponible.
- Criterio de aceptacion: `management.endpoints.web.exposure.include` contiene `health,info`.
- Forma de verificacion: revisar `src/main/resources/application.properties`.

### REQ-CUR-003 - Logs actuales en CloudWatch

- Descripcion: la infraestructura actual envia logs del contenedor a CloudWatch Logs mediante el log driver `awslogs`.
- Motivo: la observabilidad v1 debe aprovechar el destino de logs ya existente antes de proponer una plataforma nueva.
- Criterio de aceptacion: la task ECS usa `awslogs` y el log group esperado existe en Terraform.
- Forma de verificacion: revisar la definicion de ECS y `aws_cloudwatch_log_group.app` en `infra/terraform`.

### REQ-CUR-004 - Latencia funcional actual en respuesta

- Descripcion: la respuesta exitosa de `POST /questions` incluye `latencyMs`.
- Motivo: ya existe una medicion simple de latencia que puede orientar los requerimientos futuros de metricas y trazas.
- Criterio de aceptacion: `QuestionResponse` expone `latencyMs` y el controller lo calcula.
- Forma de verificacion: revisar `QuestionController.java` y `QuestionResponse.java`.

## Requisitos futuros

Estos requisitos definen el comportamiento observable deseado para v1. No afirman que ya exista.

## Reglas de privacidad

### REQ-PRIV-001 - No loggear pregunta completa

- Descripcion: ningun log debe incluir el texto completo de `question`.
- Motivo: una pregunta puede contener datos sensibles, informacion personal o contexto privado.
- Criterio de aceptacion: los eventos de log no contienen el body completo del request ni el valor completo de `question`.
- Forma de verificacion: ejecutar requests de prueba con texto unico y buscar ese texto en CloudWatch Logs o en la salida local de logs.

### REQ-PRIV-002 - No loggear respuesta completa

- Descripcion: ningun log debe incluir el texto completo de `answer`.
- Motivo: la respuesta generada puede reproducir o inferir informacion sensible.
- Criterio de aceptacion: los logs no contienen el body completo de la respuesta ni el valor completo de `answer`.
- Forma de verificacion: ejecutar una request que genere una respuesta reconocible y buscar esa respuesta en logs.

### REQ-PRIV-003 - No loggear JWTs ni secrets

- Descripcion: los logs no deben incluir JWTs, secrets, access keys, tokens ni credenciales.
- Motivo: exponer credenciales en logs crea riesgo directo de seguridad.
- Criterio de aceptacion: headers y variables sensibles no aparecen como campos de log.
- Forma de verificacion: enviar una request con header `Authorization` de prueba y confirmar que su valor no aparece en logs.

### REQ-PRIV-004 - No loggear headers sensibles

- Descripcion: headers sensibles como `Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` o equivalentes no deben registrarse completos.
- Motivo: los headers pueden contener credenciales o identificadores privados.
- Criterio de aceptacion: si se registra metadata de headers, los headers sensibles se omiten o se enmascaran.
- Forma de verificacion: enviar headers sensibles con valores identificables y buscarlos en logs.

### REQ-PRIV-005 - No incluir contenido sensible en spans

- Descripcion: los spans no deben incluir pregunta completa, respuesta completa, JWTs, secrets ni headers sensibles como atributos.
- Motivo: las trazas tambien son datos operativos persistidos y consultables.
- Criterio de aceptacion: los atributos de spans contienen metadata tecnica, no contenido sensible del usuario.
- Forma de verificacion: revisar una traza exportada y confirmar que sus atributos no contienen payloads completos ni credenciales.

## Requerimientos funcionales

### REQ-LOG-001 - Logs JSON en produccion

- Descripcion: en ambiente productivo, los logs de la aplicacion deben emitirse en formato JSON.
- Motivo: CloudWatch Logs Insights puede filtrar y agrupar mejor campos estructurados que texto libre.
- Criterio de aceptacion: cada evento operativo relevante se emite como un objeto JSON parseable.
- Forma de verificacion: inspeccionar logs de una task productiva o equivalente y confirmar que CloudWatch reconoce campos JSON.

### REQ-LOG-002 - Campos minimos de log

- Descripcion: cada log operativo de request debe incluir al menos `timestamp`, `level`, `service`, `environment`, `logger`, `message`, `requestId`, `traceId`, `spanId`, `path`, `method`, `status` y `latencyMs`.
- Motivo: estos campos permiten buscar una request, medir resultado y correlacionarla con una traza.
- Criterio de aceptacion: el log de finalizacion de `POST /questions` contiene esos campos con valores no ambiguos.
- Forma de verificacion: ejecutar una request y revisar el evento de log correspondiente.

### REQ-LOG-003 - Log unico de finalizacion de request

- Descripcion: cada request a `POST /questions` debe generar un evento principal de finalizacion.
- Motivo: un evento unico reduce ruido y controla volumen de logs.
- Criterio de aceptacion: una request exitosa produce un log operativo principal con status y latencia, sin duplicar eventos equivalentes.
- Forma de verificacion: ejecutar una request y contar los logs principales asociados al mismo `requestId`.

### REQ-LOG-004 - Logs de errores con contexto minimo

- Descripcion: los errores deben loggearse con `exceptionType`, `exceptionMessage` sanitizado, `requestId`, `traceId`, `path`, `method` y `status`.
- Motivo: un operador debe poder diagnosticar la clase de error sin revisar el payload sensible.
- Criterio de aceptacion: errores de validacion, errores de Bedrock y errores inesperados tienen logs correlacionables.
- Forma de verificacion: provocar cada tipo de error y revisar los campos presentes en logs.

### REQ-LOG-005 - Evento de arranque con metadata

- Descripcion: al iniciar, el servicio debe emitir un evento con `service`, `environment`, `release`, `aws.region` y version o commit disponible.
- Motivo: despues de un deploy, operacion debe poder saber que version esta corriendo.
- Criterio de aceptacion: cada arranque de la aplicacion deja un log estructurado con metadata de release.
- Forma de verificacion: reiniciar o desplegar la aplicacion y buscar el evento de arranque.

### REQ-CORR-001 - Generacion de requestId

- Descripcion: cada request entrante debe tener un `requestId`.
- Motivo: `requestId` permite buscar una request aunque no se tenga acceso a la herramienta de tracing.
- Criterio de aceptacion: si el cliente no envia un identificador aceptado, el backend genera uno.
- Forma de verificacion: llamar `POST /questions` sin identificador y confirmar que logs contienen `requestId`.

### REQ-CORR-002 - Reutilizacion de requestId entrante

- Descripcion: si el cliente envia un `requestId` en un header definido por el diseno, el backend debe reutilizarlo cuando sea valido.
- Motivo: esto permite correlacionar frontend, backend y reportes manuales.
- Criterio de aceptacion: el `requestId` recibido aparece igual en los logs de esa request.
- Forma de verificacion: enviar una request con un identificador de prueba y buscar ese valor en logs.

### REQ-TRACE-001 - Trace HTTP entrante

- Descripcion: cada request a `POST /questions` debe crear o continuar una traza HTTP.
- Motivo: la traza permite seguir la ejecucion end-to-end dentro del backend.
- Criterio de aceptacion: una request genera una traza con un span asociado al request HTTP entrante.
- Forma de verificacion: ejecutar una request y buscar la traza por `traceId` en la herramienta elegida.

### REQ-TRACE-002 - Trace del caso de uso

- Descripcion: la ejecucion de `AskQuestionUseCase` debe estar representada en la traza.
- Motivo: el caso de uso es la frontera de aplicacion y ayuda a separar tiempo HTTP de tiempo de negocio.
- Criterio de aceptacion: la traza muestra un span o segmento equivalente para la operacion `ask_question`.
- Forma de verificacion: revisar una traza exitosa y confirmar que existe el tramo del caso de uso.

### REQ-TRACE-003 - Trace de Bedrock

- Descripcion: la llamada a Bedrock Agent Runtime debe estar representada en la traza.
- Motivo: Bedrock es la dependencia externa critica del flujo RAG.
- Criterio de aceptacion: la traza permite distinguir el tiempo consumido por la llamada a `retrieveAndGenerate`.
- Forma de verificacion: ejecutar una request en perfil productivo o ambiente integrado y revisar el span de Bedrock.

### REQ-TRACE-004 - Errores marcados en spans

- Descripcion: cuando falla Bedrock o falla la aplicacion, el span correspondiente debe marcarse con estado de error.
- Motivo: una traza debe mostrar visualmente donde fallo la request.
- Criterio de aceptacion: errores provocados aparecen como error en el span correcto, no solo en el log.
- Forma de verificacion: forzar error de Bedrock o error inesperado y revisar la traza.

### REQ-TRACE-005 - Atributos minimos de spans

- Descripcion: los spans relevantes deben incluir atributos tecnicos como `service.name`, `app.operation`, `http.route`, `aws.region`, `bedrock.knowledge_base_id`, `bedrock.model_arn` cuando aplique, y `app.response.citations_count` cuando exista respuesta.
- Motivo: estos atributos permiten filtrar trazas sin leer payloads sensibles.
- Criterio de aceptacion: los atributos aparecen en spans sin incluir pregunta completa ni respuesta completa.
- Forma de verificacion: inspeccionar los atributos de una traza exitosa.

### REQ-METRIC-001 - Contador de requests

- Descripcion: el sistema debe medir el total de requests a `POST /questions`.
- Motivo: throughput es una senal basica de uso y salud operativa.
- Criterio de aceptacion: existe una metrica consultable con conteo de requests del endpoint.
- Forma de verificacion: ejecutar varias requests y confirmar que el contador aumenta.

### REQ-METRIC-002 - Contador de errores

- Descripcion: el sistema debe medir errores de `POST /questions` separados por resultado o tipo de excepcion.
- Motivo: permite detectar degradaciones y diferenciar validacion, Bedrock y errores inesperados.
- Criterio de aceptacion: errores 4xx/5xx o excepciones relevantes se reflejan en metricas.
- Forma de verificacion: provocar errores conocidos y validar que las series correspondientes aumentan.

### REQ-METRIC-003 - Latencia total

- Descripcion: el sistema debe medir la latencia total de `POST /questions`.
- Motivo: latencia total es necesaria para detectar degradacion visible para el usuario.
- Criterio de aceptacion: se puede consultar p50 y p95 de latencia del endpoint.
- Forma de verificacion: generar trafico de prueba y revisar percentiles en metricas o dashboard.

### REQ-METRIC-004 - Latencia de Bedrock

- Descripcion: el sistema debe medir la latencia de la llamada a Bedrock.
- Motivo: permite separar problemas del backend de problemas de la dependencia externa.
- Criterio de aceptacion: se puede consultar cuanto tarda Bedrock dentro de requests a `POST /questions`.
- Forma de verificacion: ejecutar requests integradas y revisar la metrica o span asociado a Bedrock.

### REQ-METRIC-005 - Cantidad de citas

- Descripcion: el sistema debe medir o registrar como atributo operativo la cantidad de citations devueltas por respuesta.
- Motivo: la cantidad de citas ayuda a detectar cambios en el comportamiento RAG sin registrar contenido.
- Criterio de aceptacion: una respuesta exitosa permite observar `citations_count` sin guardar snippets completos.
- Forma de verificacion: ejecutar una request exitosa y revisar metrica, log o span con el conteo.

### REQ-AWS-001 - Logs consultables en CloudWatch

- Descripcion: los logs estructurados deben poder consultarse en CloudWatch Logs.
- Motivo: CloudWatch ya es el destino actual de logs del contenedor.
- Criterio de aceptacion: es posible buscar por `requestId`, `traceId`, `status` y `exceptionType`.
- Forma de verificacion: ejecutar queries de CloudWatch Logs Insights sobre el log group del servicio.

### REQ-AWS-002 - Trazas exportadas a AWS

- Descripcion: las trazas deben exportarse a la herramienta AWS elegida para tracing, por ejemplo X-Ray o ADOT Collector con destino compatible.
- Motivo: el documento de intencion define OpenTelemetry + CloudWatch/X-Ray como stack objetivo.
- Criterio de aceptacion: una request produce una traza visible fuera del proceso de la aplicacion.
- Forma de verificacion: ejecutar una request y buscar la traza exportada por `traceId`.

### REQ-DASH-001 - Dashboard minimo

- Descripcion: debe existir un dashboard operativo minimo para `POST /questions`.
- Motivo: operacion necesita ver salud sin escribir queries manuales cada vez.
- Criterio de aceptacion: el dashboard muestra requests por minuto, latencia p50/p95, tasa de errores 5xx, errores de Bedrock, estado de health y referencia de release o deploy.
- Forma de verificacion: abrir el dashboard y validar que cada panel tiene datos recientes o una explicacion clara si no hay trafico.

### REQ-ALERT-001 - Alerta de error rate

- Descripcion: debe existir una alerta cuando la tasa de error de `POST /questions` supere un umbral definido.
- Motivo: errores sostenidos requieren accion operativa.
- Criterio de aceptacion: la alerta tiene umbral, ventana de evaluacion, severidad y destino de notificacion definidos.
- Forma de verificacion: simular o revisar una condicion de error y confirmar que la alerta cambia de estado.

### REQ-ALERT-002 - Alerta de latencia p95

- Descripcion: debe existir una alerta cuando la latencia p95 de `POST /questions` se mantenga por encima de un umbral definido.
- Motivo: la degradacion de latencia puede afectar al usuario aunque no haya errores.
- Criterio de aceptacion: la alerta evalua p95 durante una ventana sostenida.
- Forma de verificacion: generar latencia controlada en ambiente de prueba o revisar la configuracion de la alarma.

### REQ-ALERT-003 - Alerta de errores Bedrock o IAM

- Descripcion: debe existir una alerta para aumento sostenido de errores asociados a Bedrock, IAM, red o timeout.
- Motivo: Bedrock es una dependencia critica y sus fallas pueden requerir diagnostico distinto a errores de aplicacion.
- Criterio de aceptacion: errores de integracion externa pueden distinguirse de errores genericos.
- Forma de verificacion: provocar una falla de configuracion o permisos en ambiente controlado y validar senales/alerta.

### REQ-REL-001 - Metadata de release

- Descripcion: logs, metricas o trazas deben incluir metadata de release como image tag, commit SHA o version disponible.
- Motivo: permite detectar si una degradacion comenzo despues de un deploy.
- Criterio de aceptacion: al menos una senal observable permite filtrar por release.
- Forma de verificacion: desplegar una nueva version y confirmar que el identificador aparece en observabilidad.

### REQ-DOC-001 - Queries operativas documentadas

- Descripcion: deben documentarse queries basicas para buscar por `requestId`, `traceId`, errores y latencia.
- Motivo: un desarrollador junior u operador nuevo debe poder diagnosticar sin conocer de memoria CloudWatch.
- Criterio de aceptacion: la documentacion incluye ejemplos de busqueda y explica que campo usar en cada caso.
- Forma de verificacion: ejecutar las queries documentadas con datos reales o de prueba.

## Requerimientos no funcionales

### REQ-NFR-001 - Bajo overhead

- Descripcion: la observabilidad no debe agregar una sobrecarga significativa al flujo `POST /questions`.
- Motivo: el endpoint ya depende de una llamada externa potencialmente lenta; la instrumentacion no debe empeorar innecesariamente la experiencia.
- Criterio de aceptacion: el overhead de logging/tracing/metricas se mantiene bajo y no domina la latencia total.
- Forma de verificacion: comparar latencia antes y despues en ambiente controlado con trafico similar.

### REQ-NFR-002 - Compatibilidad con Spring Boot 4.0.5

- Descripcion: las dependencias de observabilidad elegidas deben ser compatibles con Spring Boot `4.0.5`.
- Motivo: dependencias incompatibles pueden romper arranque, autoconfiguracion o instrumentacion.
- Criterio de aceptacion: la aplicacion compila, arranca y ejecuta `POST /questions` con las dependencias elegidas.
- Forma de verificacion: revisar matriz de compatibilidad oficial y ejecutar tests/build al implementar.

### REQ-NFR-003 - Bajo riesgo de exponer datos sensibles

- Descripcion: la solucion debe minimizar la posibilidad de registrar datos sensibles por accidente.
- Motivo: logs, metricas y trazas suelen tener acceso mas amplio que la base de datos o el runtime.
- Criterio de aceptacion: payloads completos, credenciales y headers sensibles no se registran por defecto.
- Forma de verificacion: pruebas manuales con valores sensibles ficticios y revision de logs/trazas.

### REQ-NFR-004 - Volumen de logs controlado

- Descripcion: la solucion debe evitar logs redundantes por request y eventos de bajo valor.
- Motivo: mucho volumen aumenta costo y dificulta diagnosticar.
- Criterio de aceptacion: cada request genera una cantidad acotada de logs operativos, con eventos extra solo para errores o hitos necesarios.
- Forma de verificacion: ejecutar un conjunto de requests y contar eventos por `requestId`.

### REQ-NFR-005 - Configuracion por ambiente

- Descripcion: la observabilidad debe poder configurarse por ambiente.
- Motivo: desarrollo local, mock y produccion pueden necesitar formatos, exporters y niveles distintos.
- Criterio de aceptacion: variables o propiedades permiten activar, desactivar o ajustar logging/tracing/export por ambiente.
- Forma de verificacion: correr la aplicacion con perfiles o variables diferentes y confirmar comportamiento esperado.

## Casos de prueba manuales

### MAN-001 - Request exitosa

- Preparacion: ejecutar la aplicacion en un ambiente con perfil `mock` o con Bedrock correctamente configurado.
- Accion: llamar `POST /questions` con una pregunta valida.
- Resultado esperado: la respuesta es exitosa, existe un log estructurado con `requestId`, `traceId`, `status` y `latencyMs`, y la traza muestra HTTP, caso de uso y Bedrock cuando aplique.

### MAN-002 - Request invalida

- Preparacion: ejecutar la aplicacion normalmente.
- Accion: llamar `POST /questions` con `question` vacio o ausente.
- Resultado esperado: la respuesta es `400`, existe log correlacionable y no se registra el body completo.

### MAN-003 - Error de Bedrock

- Preparacion: usar un ambiente integrado donde se pueda provocar una falla controlada de Bedrock, IAM o configuracion.
- Accion: llamar `POST /questions` con una pregunta valida.
- Resultado esperado: la respuesta refleja el manejo actual de error, existe log con `exceptionType`, la traza marca error en el tramo de Bedrock y no se registra la pregunta completa.

### MAN-004 - Error inesperado

- Preparacion: usar un ambiente de prueba donde pueda provocarse una excepcion no controlada.
- Accion: ejecutar la request que dispara el error.
- Resultado esperado: la respuesta es `500`, el log contiene `requestId`, `traceId`, `exceptionType` y `status`, y la traza marca error.

### MAN-005 - Deploy nuevo

- Preparacion: desplegar una nueva imagen o version en ambiente controlado.
- Accion: iniciar el servicio y ejecutar una request.
- Resultado esperado: la metadata de release aparece en el evento de arranque y puede correlacionarse con logs, metricas o trazas.

### MAN-006 - Busqueda por requestId

- Preparacion: ejecutar una request y capturar su `requestId` desde logs, respuesta o header si el diseno lo expone.
- Accion: buscar ese `requestId` en CloudWatch Logs.
- Resultado esperado: aparecen los eventos relevantes de esa request y permiten entender resultado y latencia.

### MAN-007 - Busqueda por traceId

- Preparacion: ejecutar una request y capturar su `traceId`.
- Accion: buscar ese `traceId` en logs y en la herramienta de tracing.
- Resultado esperado: los logs y la traza corresponden a la misma request.

## Casos de prueba automatizables

- Validar con unit tests que el componente responsable de `requestId` genera un valor cuando no viene en la request.
- Validar con unit tests que el componente responsable de `requestId` reutiliza un valor entrante valido.
- Validar que headers sensibles no se copian al contexto de logging.
- Validar que el body completo de request no se registra en eventos de log.
- Validar que el body completo de response no se registra en eventos de log.
- Validar que errores de validacion producen metadata observable esperada sin datos sensibles.
- Validar que `QuestionProcessingException` produce metadata observable esperada.
- Validar que errores inesperados producen metadata observable esperada.
- Validar que el conteo de citations se calcula sin registrar snippets completos.
- Validar con integration tests que `POST /questions` exitoso genera contexto de correlacion.
- Validar con integration tests que `POST /questions` invalido mantiene `requestId` y status correcto.
- Validar que la configuracion por ambiente activa JSON logs/exporters solo donde corresponda.

## Criterios generales de aceptacion

La iniciativa de observabilidad v1 se considera completa cuando:

- una request exitosa a `POST /questions` puede diagnosticarse desde logs y trazas;
- una request fallida a `POST /questions` puede correlacionarse por `requestId` y `traceId`;
- los logs productivos son JSON y tienen campos minimos consistentes;
- existe un `requestId` para cada request;
- existen `traceId` y `spanId` para correlacion con tracing;
- el flujo HTTP, caso de uso y Bedrock queda representado en trazas;
- existen metricas basicas de throughput, errores y latencia;
- existe un dashboard operativo minimo;
- existen alertas iniciales para error rate, latencia y fallas relevantes de Bedrock/IAM;
- la metadata de release esta visible en al menos una senal observable;
- las reglas de privacidad fueron verificadas con pruebas manuales o automatizadas;
- no se registran preguntas completas, respuestas completas, JWTs, secrets ni headers sensibles;
- el volumen de logs por request es acotado;
- la configuracion puede variar por ambiente.

## Dependencias y decisiones pendientes

Antes de pasar al diseno tecnico o implementacion, deben validarse estas decisiones:

- Dependencias compatibles para OpenTelemetry y Spring Boot `4.0.5`.
- Si la exportacion de trazas sera directa a X-Ray o mediante ADOT Collector.
- Variables de entorno necesarias para activar tracing, logs JSON, ambiente, release y exporters.
- Permisos IAM exactos para exportar trazas y publicar metricas si aplica.
- Formato final de logs JSON y nombres exactos de campos.
- Header definitivo para recibir o devolver `requestId`.
- Estrategia para exponer `requestId` al cliente, si se decide hacerlo.
- Nombres definitivos de metricas y tags.
- Umbrales iniciales de alertas con datos reales de trafico.
- Como representar metadata de release: image tag, commit SHA, version de build o combinacion.
- Si `bedrock.knowledge_base_id` y `bedrock.model_arn` deben registrarse completos, truncados o normalizados para reducir exposicion operativa.
