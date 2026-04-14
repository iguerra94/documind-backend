terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "documind-terraform-state-253490749577-us-east-1-an"
    key            = "documind-backend/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "documind-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region  = var.aws_region
}
