# Event Flow

## Purpose

This document describes the main event-driven interactions between platform services.

## Main flow after order creation

1. The orders service validates and stores the order.
2. The orders service publishes the `OrderCreated` event.
3. The payments service consumes `OrderCreated` and starts payment processing.
4. After successful payment, a confirmation event is emitted.
5. The notifications service consumes the confirmation event and sends an email to the customer.

## Notes

The platform uses asynchronous communication between services in order to reduce coupling and improve scalability.
