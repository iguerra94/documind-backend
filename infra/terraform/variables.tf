variable "aws_region" {
  description = "Region de AWS"
  type        = string
}

variable "project_name" {
  description = "Nombre base del proyecto"
  type        = string
  default     = "documind"
}

variable "container_port" {
  description = "Puerto del contenedor"
  type        = number
  default     = 8080
}

variable "desired_count" {
  description = "Cantidad deseada de tasks ECS"
  type        = number
  default     = 0
}

variable "image_tag" {
  description = "Tag de la imagen Docker"
  type        = string
  default     = "latest"
}

variable "bedrock_knowledge_base_id" {
  description = "ID de la knowledge base de Bedrock"
  type        = string
}

variable "bedrock_model_arn" {
  description = "ARN del foundation model o inference profile soportado por Bedrock Knowledge Bases RetrieveAndGenerate"
  type        = string
}

variable "health_check_path" {
  description = "Path del health check del ALB"
  type        = string
  default     = "/actuator/health/readiness"
}
