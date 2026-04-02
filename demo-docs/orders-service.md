# Orders Service

## Purpose

The orders service is responsible for receiving, validating and registering customer orders.

## Main responsibilities

- Validate incoming order requests
- Persist orders
- Publish domain events after order creation
- Coordinate with downstream services through events

## Published events

After an order is successfully created, the service publishes the `OrderCreated` event.

If payment confirmation is received, the service may update the order status to `CONFIRMED`.

## Dependencies

The orders service depends on:

- authentication validation
- the event bus
- downstream consumers such as payments and notifications

## Security

All incoming requests must include a valid JWT token in the `Authorization` header.
