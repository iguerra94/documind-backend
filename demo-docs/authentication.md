# Authentication Guide

## Purpose

This document describes how internal services authenticate requests across the platform.

## Authentication model

All internal service-to-service requests must include a valid JWT token in the `Authorization` header using the `Bearer` scheme.

Example:
`Authorization: Bearer <token>`

## Token validation

Each service validates the token signature and expiration before processing the request.

If the token is invalid or expired, the service must reject the request with a 401 response.

## Notes

JWT tokens are issued by the internal identity service and are expected on all requests between orders, payments and notifications services.
