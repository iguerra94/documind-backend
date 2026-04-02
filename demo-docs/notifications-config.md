# Notifications Service Configuration

## Purpose

This document lists the required configuration values for the notifications service.

## Required environment variables

- `NOTIFICATIONS_QUEUE_URL`
- `EMAIL_PROVIDER_API_KEY`
- `DEFAULT_SENDER_EMAIL`
- `MAX_RETRY_ATTEMPTS`

## Behavior

The notifications service consumes messages from the notifications queue and attempts delivery through the configured email provider.

## Retry policy

If message delivery fails, the service retries up to the value defined in `MAX_RETRY_ATTEMPTS`.

After the final retry, the message is sent to the DLQ for manual inspection.
