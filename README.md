# documind-backend

Backend Spring Boot para responder preguntas mediante un flujo RAG conectado a AWS Bedrock Knowledge Bases.

## Descripcion general

`documind-backend` expone una API HTTP para recibir preguntas, delegarlas a un caso de uso de aplicacion y obtener respuestas con citas desde una implementacion del puerto `RagGateway`.

El endpoint principal confirmado es `POST /questions`. Segun el perfil activo de Spring, la respuesta se obtiene desde:

- `prod`: `BedrockRagGatewayAdapter`, que llama a AWS Bedrock Agent Runtime con `RetrieveAndGenerate`.
- `mock`: `MockRagGatewayAdapter`, que devuelve una respuesta fija para desarrollo, pruebas o ejecucion sin Bedrock.

El proyecto tambien incluye manejo global de errores, validacion de requests, endpoints de Actuator, logs operativos por request con `X-Request-Id`, infraestructura Terraform para AWS ECS Fargate y un workflow de GitHub Actions para despliegue a produccion.

## Stack tecnologico

| Area | Tecnologia |
| --- | --- |
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.5 |
| API HTTP | Spring Web MVC |
| Validacion | Spring Boot Starter Validation / Jakarta Validation |
| Observabilidad operativa | Spring Boot Actuator, logs SLF4J/Logback, `X-Request-Id` |
| Integracion AWS | AWS SDK for Java v2, `bedrockagentruntime`, `auth` |
| Build | Maven con Maven Wrapper (`mvnw`, `mvnw.cmd`) |
| Tests | JUnit 5, Spring Boot Test, MockMvc, AssertJ |
| Contenedor | Docker con `amazoncorretto:21` |
| Infraestructura | Terraform, AWS ECR, ECS Fargate, ALB, CloudWatch Logs, IAM, VPC |
| CI/CD | GitHub Actions con OIDC hacia AWS |

No se encontro base de datos configurada en el codigo actual.

## Estructura del proyecto

```text
.
├── src
│   ├── main
│   │   ├── java/com/ivan/documind/documind_backend
│   │   │   ├── DocumindBackendApplication.java
│   │   │   ├── api
│   │   │   │   ├── controller
│   │   │   │   └── dto
│   │   │   │       ├── request
│   │   │   │       └── response
│   │   │   ├── application
│   │   │   │   ├── port
│   │   │   │   └── usecase
│   │   │   ├── domain
│   │   │   │   └── model
│   │   │   ├── infrastructure
│   │   │   │   ├── adapter
│   │   │   │   │   ├── bedrock
│   │   │   │   │   └── mock
│   │   │   │   └── config
│   │   │   └── shared
│   │   │       ├── exception
│   │   │       └── observability
│   │   └── resources
│   │       └── application.properties
│   └── test
│       └── java/com/ivan/documind/documind_backend
├── demo-docs
├── docs
├── infra/terraform
├── .github/workflows/deploy.yml
├── Dockerfile
├── pom.xml
├── mvnw
└── mvnw.cmd
```

Directorios principales:

| Ruta | Proposito |
| --- | --- |
| `src/main/java/.../api/controller` | Controladores HTTP. Contiene `QuestionController`. |
| `src/main/java/.../api/dto` | DTOs de entrada y salida HTTP. |
| `src/main/java/.../application/usecase` | Casos de uso de aplicacion. Contiene `AskQuestionUseCase`. |
| `src/main/java/.../application/port` | Puertos de aplicacion. Contiene `RagGateway`. |
| `src/main/java/.../domain/model` | Modelos de dominio: `Question`, `RagAnswer`, `Citation`. |
| `src/main/java/.../infrastructure/adapter` | Adaptadores concretos para Bedrock y mock. |
| `src/main/java/.../infrastructure/config` | Configuracion de clientes externos, como `BedrockAgentRuntimeClient`. |
| `src/main/java/.../shared/exception` | Manejo global de errores y formato comun de error. |
| `src/main/java/.../shared/observability` | Filtros y utilidades de logs, request id y metadata de arranque. |
| `src/test/java` | Tests unitarios e integracion con MockMvc. |
| `demo-docs` | Documentos de ejemplo usados como material de demostracion. |
| `docs` | Documentacion tecnica, observabilidad y especificaciones. |
| `infra/terraform` | Infraestructura AWS declarada con Terraform. |

Nota: en el repo actual `QuestionRequest` esta bajo `api/dto/response` y `QuestionResponse` bajo `api/dto/request`. El README documenta esa estructura tal como existe.

## Requisitos previos

- Java 21.
- Maven Wrapper incluido en el repositorio (`./mvnw`).
- Docker, si se quiere construir o ejecutar la imagen localmente.
- Terraform `>= 1.6.0`, si se trabaja con `infra/terraform`.
- Cuenta AWS y credenciales/permisos adecuados para Bedrock, ECR, ECS, IAM, CloudWatch, VPC y ALB si se despliega en AWS.
- Una Knowledge Base de Bedrock y un modelo o inference profile compatible con `RetrieveAndGenerate` para ejecutar el perfil `prod`.

No se encontro una version minima de Docker especificada.

## Configuracion del entorno

La configuracion principal esta en `src/main/resources/application.properties`.

```properties
spring.application.name=documind-backend
app.environment=${APP_ENVIRONMENT:local}
app.release=${APP_RELEASE:dev}

server.port=8080
management.endpoints.web.exposure.include=health,info

aws.region=${AWS_REGION:us-east-1}
bedrock.knowledge-base-id=${BEDROCK_KNOWLEDGE_BASE_ID}
bedrock.model-arn=${BEDROCK_MODEL_ARN}
```

Existe un archivo local `.env`, pero esta ignorado por Git y no debe versionarse. No se encontro `.env.example` para la aplicacion; si necesitas ejecutar localmente con variables de entorno, crea tu propio `.env` local o exporta las variables en la terminal.

Variables detectadas:

| Variable | Obligatoria | Default | Uso |
| --- | --- | --- | --- |
| `APP_ENVIRONMENT` | No | `local` | Metadata de ambiente usada en logs. |
| `APP_RELEASE` | No | `dev` | Metadata de version/release usada en logs. |
| `AWS_REGION` | No | `us-east-1` | Region usada por el cliente AWS Bedrock. |
| `BEDROCK_KNOWLEDGE_BASE_ID` | Si para perfil `prod` | Sin default | ID de la Knowledge Base usada por Bedrock. |
| `BEDROCK_MODEL_ARN` | Si para perfil `prod` | Sin default | ARN del foundation model o inference profile usado por `RetrieveAndGenerate`. |
| `SPRING_PROFILES_ACTIVE` | Si para elegir adapter | Sin default | Perfil activo. Usar `mock` para respuesta local fija o `prod` para Bedrock. |

Ejemplo local con perfil mock:

```bash
SPRING_PROFILES_ACTIVE=mock ./mvnw spring-boot:run
```

Ejemplo local con variables exportadas para perfil prod:

```bash
export SPRING_PROFILES_ACTIVE=prod
export AWS_REGION=us-east-1
export BEDROCK_KNOWLEDGE_BASE_ID=<tu-knowledge-base-id>
export BEDROCK_MODEL_ARN=<tu-model-arn-o-inference-profile-arn>
./mvnw spring-boot:run
```

No incluyas secretos ni valores reales de credenciales en el repositorio.

## Instalacion

El proyecto usa Maven Wrapper, por lo que no hace falta instalar Maven globalmente.

```bash
./mvnw clean package
```

Este comando compila, ejecuta tests y genera el JAR en `target/`.

Para ejecutar solo la instalacion/validacion de dependencias sin empaquetar manualmente:

```bash
./mvnw test
```

## Ejecucion local

### Modo mock

El modo mock no llama a Bedrock y es el camino mas simple para levantar la API localmente.

```bash
SPRING_PROFILES_ACTIVE=mock ./mvnw spring-boot:run
```

La aplicacion escucha en el puerto `8080`.

Validacion rapida:

```bash
curl http://localhost:8080/actuator/health
```

Probar el endpoint principal:

```bash
curl -X POST http://localhost:8080/questions \
  -H "Content-Type: application/json" \
  -d '{"question":"What does the orders service do?"}'
```

### Modo prod

El modo `prod` activa `BedrockRagGatewayAdapter` y requiere configuracion de AWS y Bedrock.

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Para que funcione, el entorno debe tener credenciales AWS resolubles por el AWS SDK y las variables `BEDROCK_KNOWLEDGE_BASE_ID` y `BEDROCK_MODEL_ARN`.

## API

### `POST /questions`

Recibe una pregunta y devuelve una respuesta con citas y latencia.

Request:

```json
{
  "question": "What does the orders service do?"
}
```

Validacion:

- `question` es obligatorio.
- `question` no puede estar vacio ni contener solo espacios.
- El mensaje de validacion configurado es `question must have a value`.

Response exitosa:

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

| Metodo | Ruta | Proposito | Autenticacion |
| --- | --- | --- | --- |
| `POST` | `/questions` | Responder una pregunta usando el puerto `RagGateway`. | No hay autenticacion implementada en el codigo actual. |

No se encontro Swagger, OpenAPI ni coleccion Postman en el repositorio.

## Errores

El manejo global esta centralizado en `GlobalExceptionHandler`.

Formato de error:

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

Errores manejados:

| Caso | HTTP | Mensaje principal |
| --- | --- | --- |
| Validacion fallida | `400` | `Validation failed` |
| JSON malformado | `400` | `Malformed JSON request` |
| `QuestionProcessingException` | `400` | Mensaje de la excepcion |
| Excepcion no controlada | `500` | `An unexpected error occurred` |

El adapter de Bedrock transforma `BedrockAgentRuntimeException` en `QuestionProcessingException`. Hay un mensaje especifico cuando Bedrock indica que el model ARN no es compatible con Knowledge Bases `RetrieveAndGenerate`.

## Observabilidad

El proyecto incluye observabilidad operativa basica:

- Actuator con endpoints expuestos `health` e `info`.
- Filtro `RequestIdFilter`, que genera o reutiliza el header `X-Request-Id`.
- Filtro `RequestCompletionLoggingFilter`, que registra un evento `request_completed` por request con servicio, ambiente, release, request id, metodo, path, status, outcome y latencia.
- `ApplicationStartupLogger`, que registra `application_started` con servicio, ambiente, release, region AWS y perfiles activos.
- `GlobalExceptionHandler`, que registra `request_failed` para errores manejados.
- Tests que verifican que no se registren bodies de request ni headers sensibles como `Authorization`.

Ejemplo de header soportado:

```http
X-Request-Id: client-request_123.abc
```

No se encontro configuracion de OpenTelemetry, tracing distribuido, logs JSON estructurados, dashboards ni alertas versionadas.

## Base de datos y migraciones

No se encontro base de datos configurada en el codigo actual.

No se encontraron migraciones, seeds ni herramientas como Flyway o Liquibase.

## Testing

Ejecutar todos los tests:

```bash
./mvnw test
```

Estado verificado localmente:

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

Los tests estan en `src/test/java` y cubren:

- generacion, reutilizacion y reemplazo de `X-Request-Id`;
- logs de finalizacion de requests;
- logs de arranque de aplicacion;
- logs de errores del `GlobalExceptionHandler`;
- integracion de `POST /questions` con perfil `mock` y MockMvc.

## Calidad de codigo

No se encontraron scripts especificos de lint, format o typecheck en `pom.xml`.

Comandos Maven disponibles por convencion del proyecto:

```bash
./mvnw test
./mvnw clean package
```

## Scripts disponibles

El proyecto no define scripts en `package.json`, `Makefile`, `Taskfile` u otro archivo equivalente. Los comandos principales salen de Maven Wrapper:

| Comando | Descripcion |
| --- | --- |
| `./mvnw test` | Ejecuta los tests. |
| `./mvnw clean package` | Limpia, compila, ejecuta tests y genera el JAR. |
| `./mvnw spring-boot:run` | Levanta la aplicacion localmente. Requiere definir un perfil util, por ejemplo `SPRING_PROFILES_ACTIVE=mock`. |

## Docker

El `Dockerfile`:

- usa `amazoncorretto:21`;
- define `WORKDIR /app`;
- copia `target/*.jar` como `app.jar`;
- expone el puerto `8080`;
- ejecuta `java -jar /app/app.jar`.

Antes de construir la imagen debe existir un JAR en `target/`.

```bash
./mvnw clean package
docker build -t documind-backend:local .
```

Ejecutar con perfil mock:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=mock \
  documind-backend:local
```

Ejecutar con variables desde `.env` local:

```bash
docker run --rm -p 8080:8080 --env-file .env documind-backend:local
```

## Infraestructura y despliegue

La infraestructura esta en `infra/terraform`.

Terraform define:

- ECR repository `${var.project_name}-backend`;
- VPC `10.0.0.0/16`;
- dos subnets publicas y dos privadas;
- Internet Gateway y NAT Gateway;
- security group para ALB y security group para ECS;
- Application Load Balancer publico;
- target group HTTP con `target_type = "ip"`;
- CloudWatch Log Group `/ecs/${var.project_name}` con retencion de 14 dias;
- ECS Cluster;
- ECS Task Definition Fargate;
- ECS Service Fargate;
- IAM execution role;
- IAM task role con permisos de Bedrock.

Variables de Terraform:

| Variable | Default | Descripcion |
| --- | --- | --- |
| `aws_region` | Sin default | Region AWS. |
| `project_name` | `documind` | Nombre base del proyecto. |
| `container_port` | `8080` | Puerto del contenedor. |
| `desired_count` | `0` | Cantidad deseada de tasks ECS. |
| `image_tag` | `latest` | Tag de la imagen Docker. |
| `bedrock_knowledge_base_id` | Sin default | ID de la Knowledge Base de Bedrock. |
| `bedrock_model_arn` | Sin default | ARN del modelo o inference profile. |
| `health_check_path` | `/actuator/health/readiness` | Path usado por el health check del ALB. |

Outputs de Terraform:

| Output | Descripcion |
| --- | --- |
| `alb_dns_name` | DNS publico del ALB. |
| `ecr_repository_url` | URL del repositorio ECR. |
| `ecs_cluster_name` | Nombre del cluster ECS. |
| `ecs_service_name` | Nombre del servicio ECS. |

El backend remoto de Terraform esta configurado en `infra/terraform/providers.tf` con S3 y DynamoDB.

Comandos Terraform habituales:

```bash
cd infra/terraform
terraform init
terraform plan
terraform apply
```

### GitHub Actions

El workflow `.github/workflows/deploy.yml` se ejecuta en pushes a `main`, ignorando cambios de documentacion como `README.md`, `docs/**`, `demo-docs/**` y `HELP.md`.

El job:

1. valida variables de GitHub (`AWS_REGION`, `AWS_ROLE_TO_ASSUME`, `BEDROCK_KNOWLEDGE_BASE_ID`, `BEDROCK_MODEL_ARN`);
2. configura Java 21;
3. ejecuta `./mvnw clean package`;
4. asume un rol AWS mediante OIDC;
5. hace login en ECR;
6. construye, etiqueta y sube la imagen Docker con `${{ github.sha }}`;
7. ejecuta `terraform init`, `terraform plan` y `terraform apply`;
8. espera estabilidad del servicio ECS;
9. verifica el endpoint de readiness del ALB.

## Autenticacion y seguridad

No hay autenticacion implementada en el backend actual. El endpoint `POST /questions` no requiere JWT, sesion ni API key en el codigo existente.

Controles y consideraciones existentes:

- El request body se valida con `@NotBlank` para `question`.
- Los errores se centralizan en `GlobalExceptionHandler`.
- `RequestIdFilter` solo acepta valores de `X-Request-Id` con caracteres alfanumericos, punto, guion bajo o guion medio, con longitud de 1 a 128.
- Los tests verifican que no se registren valores sensibles como `Authorization`, tokens o cuerpos completos de requests en logs operativos.
- La autenticacion con Cognito/JWT aparece documentada como objetivo futuro en `docs/chat-frontend-and-history.md`, pero no esta implementada en el codigo actual.

## Flujo de desarrollo recomendado

1. Crear una rama de trabajo, por ejemplo `docs/readme` o `feature/<nombre>`.
2. Ejecutar los tests antes de modificar comportamiento:

```bash
./mvnw test
```

3. Levantar localmente con perfil mock:

```bash
SPRING_PROFILES_ACTIVE=mock ./mvnw spring-boot:run
```

4. Probar `POST /questions` y `/actuator/health`.
5. Implementar cambios.
6. Volver a ejecutar:

```bash
./mvnw test
./mvnw clean package
```

7. Si el cambio afecta despliegue, revisar `Dockerfile`, `.github/workflows/deploy.yml` e `infra/terraform`.

## Troubleshooting

### No arranca la aplicacion por falta de `RagGateway`

Si no hay perfil activo, puede no existir un bean de `RagGateway`, porque los adapters estan condicionados por perfil:

- `SPRING_PROFILES_ACTIVE=mock` para desarrollo local sin Bedrock.
- `SPRING_PROFILES_ACTIVE=prod` para Bedrock.

### Faltan variables de Bedrock en `prod`

El perfil `prod` requiere `BEDROCK_KNOWLEDGE_BASE_ID` y `BEDROCK_MODEL_ARN`. Si faltan o son invalidas, la aplicacion puede fallar al iniciar o al procesar preguntas.

### Error de model ARN no soportado

Si Bedrock responde que el model ARN no es soportado, el adapter devuelve un mensaje indicando que `BEDROCK_MODEL_ARN` debe ser un foundation model o inference profile soportado por Knowledge Bases `RetrieveAndGenerate` en la misma region que `AWS_REGION`.

### Health check de Terraform apunta a readiness

Terraform usa por defecto:

```text
/actuator/health/readiness
```

La configuracion actual de Spring expone:

```properties
management.endpoints.web.exposure.include=health,info
```

Antes de depender de ese path en ALB o GitHub Actions, valida que `/actuator/health/readiness` responda correctamente en el entorno objetivo o ajusta `health_check_path`.

### El contenedor no encuentra el JAR

El `Dockerfile` copia `target/*.jar`. Ejecuta primero:

```bash
./mvnw clean package
```

### El workflow no corre con cambios de documentacion

`.github/workflows/deploy.yml` ignora pushes que solo cambian Markdown, `docs/**`, `demo-docs/**`, `README.md` o `HELP.md`.

## Documentacion adicional

| Archivo | Contenido |
| --- | --- |
| `docs/project-technical-overview.md` | Baseline tecnico del proyecto y arquitectura actual. |
| `docs/observability-backend-aws.md` | Plan de observabilidad para backend en AWS. |
| `docs/chat-frontend-and-history.md` | Propuesta futura para frontend, autenticacion e historial persistido. |
| `docs/specs/observability` | Requisitos, diseno y tareas de observabilidad. |
| `deploy-aws-ecs-fargate.md` | Guia extensa de despliegue en AWS ECS Fargate. |
| `demo-docs` | Documentos de ejemplo para demostraciones. |

## Contribucion

No se encontro una guia formal de contribucion en el repositorio.

Guia practica sugerida para este proyecto:

1. Trabajar en una rama separada.
2. Mantener los cambios acotados al objetivo.
3. Ejecutar `./mvnw test` antes de abrir un PR.
4. Actualizar documentacion cuando cambien endpoints, variables de entorno, infraestructura o comandos operativos.
5. No versionar `.env`, archivos `*.tfvars` con valores reales ni credenciales.

## Licencia

No se encontro informacion de licencia en el repositorio.
