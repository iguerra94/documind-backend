# Documentacion tecnica del proyecto

## Objetivo del documento

Este documento describe el estado tecnico actual de `documind-backend` antes de implementar mejoras de observabilidad.

La intencion es dejar una linea base clara para entender como esta armado el proyecto hoy, que flujo ejecuta el endpoint principal, como se integra con AWS Bedrock y que infraestructura existe en Terraform. Este documento no describe la solucion futura de observabilidad; solamente documenta lo que esta confirmado en el repositorio.

## Resumen del proyecto

`documind-backend` es un backend Spring Boot que expone un endpoint para responder preguntas usando un flujo RAG.

El endpoint principal es `POST /questions`. Recibe una pregunta, la envia al caso de uso `AskQuestionUseCase` y obtiene una respuesta desde una implementacion del puerto `RagGateway`.

El proyecto tiene dos adapters para ese puerto:

- `BedrockRagGatewayAdapter`, activo con el perfil `prod`, que llama a AWS Bedrock Agent Runtime.
- `MockRagGatewayAdapter`, activo con el perfil `mock`, que devuelve una respuesta fija para pruebas o ejecucion sin Bedrock.

## Stack tecnico

- Lenguaje: Java.
- Version de Java: 21, configurada en `pom.xml`.
- Framework: Spring Boot `4.0.5`.
- Build tool: Maven, con wrapper `mvnw` y `mvnw.cmd`.
- Runtime del contenedor: `amazoncorretto:21`, definido en `Dockerfile`.
- Servidor HTTP: Spring Boot Web MVC.
- Validacion: `spring-boot-starter-validation`.
- Health checks y endpoints operativos: `spring-boot-starter-actuator`.
- AWS SDK:
  - `software.amazon.awssdk:bedrockagentruntime:2.41.34`
  - `software.amazon.awssdk:auth:2.41.34`
- Perfiles de Spring confirmados en codigo:
  - `prod`: usa `BedrockRagGatewayAdapter`.
  - `mock`: usa `MockRagGatewayAdapter`.

## Arquitectura actual

El proyecto esta organizado en capas. La estructura principal esta bajo el paquete base `com.ivan.documind.documind_backend`.

### `api/controller`

Contiene los controladores HTTP.

Archivo principal:

- `QuestionController.java`

Responsabilidad:

- Exponer `POST /questions`.
- Recibir el request HTTP.
- Validar el DTO de entrada con `@Valid`.
- Llamar al caso de uso `AskQuestionUseCase`.
- Transformar el modelo de dominio `RagAnswer` en el DTO de respuesta `QuestionResponse`.
- Calcular una latencia simple del request usando `System.currentTimeMillis()`.

### `api/dto`

Contiene DTOs usados para entrada y salida HTTP.

Archivos:

- `QuestionRequest.java`
- `QuestionResponse.java`
- `CitationResponse.java`

Responsabilidad:

- Definir el contrato JSON del endpoint.
- Aplicar validaciones simples de entrada.
- Separar el contrato HTTP de los modelos de dominio.

Observacion: los paquetes actuales tienen una nomenclatura invertida o confusa. `QuestionRequest` esta bajo `api/dto/response`, mientras que `QuestionResponse` esta bajo `api/dto/request`. Esto esta confirmado en el repo.

### `application/usecase`

Contiene la logica de aplicacion.

Archivo principal:

- `AskQuestionUseCase.java`

Responsabilidad:

- Recibir la pregunta en formato `String`.
- Crear el objeto de dominio `Question`.
- Delegar la resolucion al puerto `RagGateway`.

Actualmente el caso de uso es delgado y no contiene reglas adicionales de negocio mas alla de construir el objeto `Question`.

### `application/port`

Contiene contratos que la aplicacion necesita para comunicarse con infraestructura externa.

Archivo:

- `RagGateway.java`

Responsabilidad:

- Definir la operacion `ask(Question question)`.
- Permitir que el caso de uso no dependa directamente de Bedrock ni de una implementacion concreta.

### `domain/model`

Contiene modelos de dominio simples.

Archivos:

- `Question.java`
- `RagAnswer.java`
- `Citation.java`

Responsabilidad:

- Representar los conceptos principales del flujo RAG:
  - una pregunta
  - una respuesta
  - una cita o referencia

Actualmente son `record` de Java sin validaciones internas adicionales.

### `infrastructure/adapter`

Contiene implementaciones concretas de puertos de aplicacion.

Archivos principales:

- `BedrockRagGatewayAdapter.java`
- `MockRagGatewayAdapter.java`

Responsabilidad:

- Implementar `RagGateway`.
- Conectar la aplicacion con AWS Bedrock en produccion.
- Permitir una respuesta mockeada con el perfil `mock`.

### `infrastructure/config`

Contiene configuraciones de infraestructura.

Archivo:

- `BedrockConfig.java`

Responsabilidad:

- Crear el bean `BedrockAgentRuntimeClient`.
- Configurar la region AWS desde la propiedad `aws.region`.

### `shared/exception`

Contiene excepciones y manejo global de errores.

Archivos:

- `GlobalExceptionHandler.java`
- `QuestionProcessingException.java`
- `ApiErrorResponse.java`

Responsabilidad:

- Centralizar respuestas de error HTTP.
- Mapear excepciones conocidas a respuestas consistentes.
- Definir el formato comun de error.

## Flujo actual de `POST /questions`

El flujo confirmado en el codigo es el siguiente:

1. El cliente llama a `POST /questions`.
2. `QuestionController` recibe el request.
3. Spring valida el cuerpo usando `@Valid`.
4. `QuestionRequest.question` debe tener valor porque usa `@NotBlank`.
5. El controller inicia una medicion de tiempo con `System.currentTimeMillis()`.
6. El controller llama a `AskQuestionUseCase.execute(questionRequest.question())`.
7. `AskQuestionUseCase` crea un objeto de dominio `Question`.
8. `AskQuestionUseCase` llama a `ragGateway.ask(question)`.
9. La implementacion concreta de `RagGateway` depende del perfil activo:
   - con `prod`, Spring usa `BedrockRagGatewayAdapter`;
   - con `mock`, Spring usa `MockRagGatewayAdapter`.
10. En `prod`, `BedrockRagGatewayAdapter` construye un `RetrieveAndGenerateRequest`.
11. El adapter llama a `client.retrieveAndGenerate(request)`.
12. El adapter transforma la respuesta de Bedrock en `RagAnswer`.
13. El controller transforma las citas de dominio en `CitationResponse`.
14. El controller calcula `latencyMs`.
15. El controller devuelve `QuestionResponse`.

### Request esperado

Segun `QuestionRequest.java`, el body esperado es:

```json
{
  "question": "What does the orders service do?"
}
```

Regla actual:

- `question` no puede estar vacio ni contener solamente espacios.

Mensaje de validacion configurado:

```text
question must have a value
```

### Response esperado

Segun `QuestionResponse.java` y `CitationResponse.java`, la respuesta exitosa tiene esta forma:

```json
{
  "answer": "Texto de respuesta",
  "citations": [
    {
      "title": "Nombre o URI de la fuente",
      "snippet": "Fragmento usado como referencia"
    }
  ],
  "latencyMs": 123
}
```

`latencyMs` se calcula en el controller y mide el tiempo desde antes de llamar al caso de uso hasta despues de obtener el `RagAnswer`.

## Integracion con AWS Bedrock

La integracion productiva con Bedrock esta en `BedrockRagGatewayAdapter.java`.

### Clase responsable

- `BedrockRagGatewayAdapter`

Esta clase:

- implementa `RagGateway`;
- esta anotada con `@Component`;
- esta activa solo con `@Profile("prod")`;
- usa `BedrockAgentRuntimeClient`.

### Cliente usado

El cliente AWS se crea en `BedrockConfig.java`:

```java
BedrockAgentRuntimeClient.builder()
    .region(Region.of(region))
    .build();
```

La region sale de la propiedad `aws.region`.

### Variables de configuracion requeridas

Las propiedades usadas por la aplicacion son:

- `aws.region`
- `bedrock.knowledge-base-id`
- `bedrock.model-arn`

En `application.properties`, esas propiedades se resuelven desde variables de entorno:

- `AWS_REGION`, con default `us-east-1`
- `BEDROCK_KNOWLEDGE_BASE_ID`, sin default
- `BEDROCK_MODEL_ARN`, sin default

### Knowledge base id

El adapter recibe el valor desde:

```properties
bedrock.knowledge-base-id=${BEDROCK_KNOWLEDGE_BASE_ID}
```

Ese valor se usa en `knowledgeBaseConfiguration.knowledgeBaseId(...)`.

### Model ARN

El adapter recibe el valor desde:

```properties
bedrock.model-arn=${BEDROCK_MODEL_ARN}
```

Ese valor se usa en `knowledgeBaseConfiguration.modelArn(...)`.

### Region AWS

La region se configura en:

```properties
aws.region=${AWS_REGION:us-east-1}
```

Terraform tambien define la variable `aws_region` y la inyecta como variable de entorno `AWS_REGION` en la task ECS.

### Manejo actual de errores de Bedrock

`BedrockRagGatewayAdapter` captura `BedrockAgentRuntimeException` y la transforma en `QuestionProcessingException`.

Existe un caso especial:

- si el mensaje contiene `The model arn provided is not supported`, se devuelve un mensaje mas explicativo indicando que `BEDROCK_MODEL_ARN` no es valido para Knowledge Bases `RetrieveAndGenerate`.

Para otros errores de Bedrock, se devuelve:

```text
Bedrock error: <mensaje original>
```

### Restricciones actuales

- El adapter asume que `response.citations()` existe y que cada citation tiene al menos un elemento en `retrievedReferences()`.
- No hay manejo defensivo si Bedrock devuelve citas vacias o una estructura inesperada.
- No hay timeout configurado explicitamente en el cliente Bedrock.
- No hay retry policy custom confirmada en el repo.
- No hay logs especificos alrededor de la llamada a Bedrock.
- No hay trazas distribuidas alrededor de `retrieveAndGenerate`.

## Configuracion de la aplicacion

El archivo `src/main/resources/application.properties` contiene:

```properties
spring.application.name=documind-backend

server.port=8080
management.endpoints.web.exposure.include=health,info

aws.region=${AWS_REGION:us-east-1}
bedrock.knowledge-base-id=${BEDROCK_KNOWLEDGE_BASE_ID}
bedrock.model-arn=${BEDROCK_MODEL_ARN}
```

### Propiedades relevantes

- `spring.application.name`: define el nombre logico de la aplicacion como `documind-backend`.
- `server.port`: configura el puerto HTTP `8080`.
- `management.endpoints.web.exposure.include`: expone `health` e `info` de Actuator.
- `aws.region`: toma la region desde `AWS_REGION` o usa `us-east-1` como default.
- `bedrock.knowledge-base-id`: toma el ID de la knowledge base desde `BEDROCK_KNOWLEDGE_BASE_ID`.
- `bedrock.model-arn`: toma el ARN del modelo desde `BEDROCK_MODEL_ARN`.

## Manejo actual de errores

El manejo global de errores esta centralizado en `GlobalExceptionHandler.java` mediante `@RestControllerAdvice`.

### Errores de validacion

`MethodArgumentNotValidException` se responde con HTTP `400 Bad Request`.

La respuesta incluye una lista `details` con errores por campo, usando el formato:

```text
<campo>: <mensaje>
```

### JSON malformado

`HttpMessageNotReadableException` se responde con HTTP `400 Bad Request`.

El mensaje principal es:

```text
Malformed JSON request
```

`details` incluye el mensaje de la causa mas especifica.

### `QuestionProcessingException`

`QuestionProcessingException` se responde con HTTP `400 Bad Request`.

Actualmente se usa para errores al procesar preguntas, incluyendo errores mapeados desde Bedrock.

### Errores genericos

Cualquier otra `Exception` se responde con HTTP `500 Internal Server Error`.

El mensaje principal enviado al cliente es:

```text
An unexpected error occurred
```

`details` incluye `exception.getMessage()`.

### Formato de error

El formato comun esta definido en `ApiErrorResponse.java`:

```json
{
  "timestamp": "2026-04-17T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/questions",
  "details": ["question: question must have a value"]
}
```

El timestamp se genera con `LocalDateTime.now()`.

## Observabilidad actual

Estado confirmado actualmente:

- Actuator esta instalado mediante `spring-boot-starter-actuator`.
- `application.properties` expone los endpoints `health` e `info`.
- El `Dockerfile` expone el puerto `8080`.
- Terraform configura logs del contenedor hacia CloudWatch Logs usando el log driver `awslogs`.
- Terraform crea el log group `/ecs/${var.project_name}` con retencion de 14 dias.
- El documento de deploy menciona logs en CloudWatch.

Limitaciones actuales de observabilidad:

- No hay logging JSON estructurado confirmado en el repo.
- No hay `requestId` explicito por request.
- No hay `traceId` ni `spanId` explicitos.
- No hay OpenTelemetry.
- No hay tracing distribuido.
- No hay spans manuales en controller, caso de uso o Bedrock.
- No hay metricas funcionales de negocio para `POST /questions`.
- No hay dashboard confirmado en Terraform o en otro archivo del repo.
- No hay alertas confirmadas en Terraform o en otro archivo del repo.

Observacion importante:

- `deploy-aws-ecs-fargate.md` recomienda endpoints de liveness/readiness, pero `application.properties` solo expone `health,info`. No hay configuracion especifica de probes de liveness/readiness confirmada en el archivo actual.

## Infraestructura AWS actual

La infraestructura esta declarada en `infra/terraform`.

### Terraform y provider

`providers.tf` define:

- Terraform requerido `>= 1.6.0`.
- Provider AWS `~> 5.0`.
- Backend remoto S3 para el estado de Terraform.
- Locking con DynamoDB.
- Region del provider desde `var.aws_region`.

Backend remoto configurado:

- bucket: `documind-terraform-state-253490749577-us-east-1-an`
- key: `documind-backend/terraform.tfstate`
- region: `us-east-1`
- dynamodb_table: `documind-terraform-locks`
- encrypt: `true`

### ECR

Terraform crea un repositorio ECR:

- recurso: `aws_ecr_repository.app`
- nombre: `${var.project_name}-backend`
- scan on push habilitado.

### Red

Terraform crea:

- VPC `10.0.0.0/16`.
- Dos subnets publicas:
  - `10.0.1.0/24`
  - `10.0.2.0/24`
- Dos subnets privadas:
  - `10.0.11.0/24`
  - `10.0.12.0/24`
- Internet Gateway.
- NAT Gateway en la subnet publica A.
- Route table publica con salida a Internet Gateway.
- Route table privada con salida a NAT Gateway.

### Security Groups

Terraform crea:

- Security group del ALB:
  - permite ingreso HTTP `80` desde `0.0.0.0/0`;
  - permite egreso general.
- Security group de ECS:
  - permite ingreso al puerto del contenedor solo desde el security group del ALB;
  - permite egreso general.

### Application Load Balancer

Terraform crea:

- `aws_lb.main`, publico, de tipo `application`.
- Listener HTTP en puerto `80`.
- Target group HTTP con `target_type = "ip"`.
- Health check configurable mediante `var.health_check_path`.

El valor default de `health_check_path` es:

```text
/actuator/health/readiness
```

### CloudWatch Logs

Terraform crea:

- `aws_cloudwatch_log_group.app`
- nombre: `/ecs/${var.project_name}`
- retencion: 14 dias

La task ECS usa log driver `awslogs` con:

- `awslogs-group`
- `awslogs-region`
- `awslogs-stream-prefix = "ecs"`

### ECS

Terraform crea:

- ECS cluster: `${var.project_name}-cluster`.
- Task definition Fargate.
- ECS service Fargate.

Task definition:

- `requires_compatibilities = ["FARGATE"]`
- `network_mode = "awsvpc"`
- CPU: `512`
- memory: `1024`
- container image: `${aws_ecr_repository.app.repository_url}:${var.image_tag}`
- container port: `var.container_port`
- `SPRING_PROFILES_ACTIVE=prod`

ECS service:

- launch type: `FARGATE`
- desired count desde `var.desired_count`
- subnets privadas
- sin public IP
- conectado al target group del ALB
- deployment circuit breaker habilitado con rollback
- health check grace period de 90 segundos

### IAM

Terraform crea dos roles:

- `ecs_execution_role`
- `ecs_task_role`

El execution role tiene adjunta la policy administrada:

```text
arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

El task role tiene una policy inline `bedrock_access` con permisos:

- `bedrock:GetInferenceProfile`
- `bedrock:InvokeModel`
- `bedrock:InvokeModelWithResponseStream`
- `bedrock:Retrieve`
- `bedrock:RetrieveAndGenerate`

El recurso esta configurado como:

```text
Resource = "*"
```

### Variables relevantes de Terraform

Variables definidas en `variables.tf`:

- `aws_region`
- `project_name`, default `documind`
- `container_port`, default `8080`
- `desired_count`, default `0`
- `image_tag`, default `latest`
- `bedrock_knowledge_base_id`
- `bedrock_model_arn`
- `health_check_path`, default `/actuator/health/readiness`

### Outputs de Terraform

Outputs definidos:

- `alb_dns_name`
- `ecr_repository_url`
- `ecs_cluster_name`
- `ecs_service_name`

## Deploy actual

### Confirmado por `Dockerfile`

El contenedor:

- usa la imagen base `amazoncorretto:21`;
- define `WORKDIR /app`;
- copia `target/*.jar` como `app.jar`;
- expone el puerto `8080`;
- ejecuta `java -jar /app/app.jar`.

Esto implica que antes de construir la imagen Docker debe existir un JAR generado en `target`.

### Confirmado por Terraform

El despliegue en AWS usa:

- ECR para almacenar la imagen;
- ECS Fargate para correr el contenedor;
- ALB publico para recibir trafico HTTP;
- subnets privadas para las tasks ECS;
- CloudWatch Logs para logs del contenedor;
- variables de entorno para AWS, Bedrock y perfil Spring.

### Confirmado por `deploy-aws-ecs-fargate.md`

El documento de deploy describe como objetivo:

- correr la aplicacion en ECS Fargate;
- usar un Application Load Balancer publico;
- tener health checks;
- enviar logs a CloudWatch;
- configurar despliegue automatico desde GitHub Actions al hacer merge a `main`.

### No confirmado en el repo

- No se encontro workflow de GitHub Actions en los archivos listados.
- No se confirmo un script automatizado de build y push de imagen.
- No se confirmo una configuracion actual de CI/CD versionada en el repo.
- No se confirmo dashboard de CloudWatch.
- No se confirmo configuracion de alertas.

## Limitaciones actuales

- `README.md` contiene solamente el titulo del proyecto.
- No hay documentacion de arquitectura previa, fuera de documentos relacionados a deploy y observabilidad.
- No se encontraron tests en `src/test`.
- No hay correlacion explicita de requests mediante `requestId`.
- No hay trazabilidad distribuida.
- No hay OpenTelemetry.
- No hay metricas funcionales de negocio.
- No hay logs JSON estructurados.
- No hay logs especificos para diferenciar con claridad errores de aplicacion, Bedrock, IAM, red o timeout.
- El controller calcula `latencyMs`, pero no lo registra en logs ni metricas.
- El adapter de Bedrock esta acoplado a que cada citation tenga al menos una referencia recuperada.
- El manejo de errores de Bedrock depende parcialmente del texto del mensaje de excepcion.
- `QuestionProcessingException` se responde como HTTP 400, incluso cuando el origen podria ser una falla externa de Bedrock o infraestructura.
- La configuracion actual expone `health,info`, pero el health check default de Terraform apunta a `/actuator/health/readiness`.
- No hay metadata de release expuesta o loggeada desde la aplicacion.

## Relacion con la futura iniciativa de observabilidad

Este documento funciona como baseline tecnico antes de implementar `docs/observability-backend-aws.md`.

Sirve para comparar el estado actual contra las mejoras futuras. Por ejemplo:

- hoy no hay `requestId`; la iniciativa de observabilidad deberia agregarlo;
- hoy no hay `traceId` ni `spanId`; la iniciativa deberia incorporarlos mediante tracing;
- hoy no hay metricas funcionales de `POST /questions`; la iniciativa deberia definirlas;
- hoy los logs llegan a CloudWatch, pero no son JSON estructurado; la iniciativa deberia mejorar su formato;
- hoy existe infraestructura ECS y CloudWatch Logs; la iniciativa puede apoyarse en esa base.

La documentacion futura deberia usar este archivo como punto de partida para justificar cambios, separar decisiones nuevas de comportamiento existente y validar que cada mejora tenga un criterio de aceptacion claro.

## Proximos documentos recomendados

- `docs/specs/observability/requirements.md`
- `docs/specs/observability/design.md`
- `docs/specs/observability/tasks.md`
- `docs/observability-runbook.md`
