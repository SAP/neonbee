# OpenTelemetry (Tracing & Metrics)

## Content

- [OpenTelemetry (Tracing \& Metrics)](#opentelemetry-tracing--metrics)
  - [Content](#content)
  - [Overview](#overview)
  - [Enabling telemetry](#enabling-telemetry)
    - [Via the `io.neonbee.NeonBee.yaml` config file](#via-the-ioneonbeeneonbeeyaml-config-file)
    - [Via the command line](#via-the-command-line)
  - [Configuration reference](#configuration-reference)
  - [Required JVM system property](#required-jvm-system-property)
  - [Forwarding to Dynatrace](#forwarding-to-dynatrace)
  - [What is traced](#what-is-traced)
  - [Metrics](#metrics)

## Overview

NeonBee can export **traces** and **metrics** using [OpenTelemetry](https://opentelemetry.io/). Telemetry is
**opt-in** and disabled by default.

- **Traces** are produced by Vert.x (HTTP server/client requests and event bus / verticle communication) and exported
  through the OpenTelemetry SDK using an OTLP `http/protobuf` span exporter.
- **Metrics** reuse the existing Micrometer meters (all Vert.x and NeonBee meters) and are forwarded through an
  additional OTLP Micrometer registry, so no re-instrumentation is required.

Both signals are sent to the same OTLP endpoint (e.g. a Dynatrace environment or a local OpenTelemetry Collector).

## Enabling telemetry

### Via the `io.neonbee.NeonBee.yaml` config file

```yaml
# Other settings omitted for simplicity.
tracing:
  enabled: true
  otlpEndpoint: https://<environment-id>.live.dynatrace.com/api/v2/otlp
  otlpApiToken: dt0c01.<token>
  serviceName: my-neonbee-service   # optional, defaults to "auto" (see below)
  exportTraces: true                # optional, defaults to true
  exportMetrics: true               # optional, defaults to true
  exportIntervalSeconds: 60         # optional, defaults to 60
```

### Via the command line

The `--enable-tracing` (short: `-tracing`) flag force-enables telemetry. The endpoint and API token are **not**
exposed on the command line (they are secrets) and must be provided through the config file (or environment, see
below):

```sh
java -jar neonbee.jar --enable-tracing
```

When both are present, a command line force-enable is preserved even if the config file sets `enabled: false`; the
endpoint/token from the config file are still applied.

## Configuration reference

| Property                | Type    | Required | Description                                                                                                   |
|:------------------------|:-------:|:--------:|:--------------------------------------------------------------------------------------------------------------|
| `enabled`               | boolean | No       | Whether telemetry is enabled. Defaults to `false` (opt-in).                                                   |
| `exportTraces`          | boolean | No       | Whether traces are exported when telemetry is enabled. Defaults to `true`.                                    |
| `exportMetrics`         | boolean | No       | Whether metrics are exported when telemetry is enabled. Defaults to `true`.                                   |
| `serviceName`           | string  | No       | The `service.name` resource attribute. If unset, falls back to `OTEL_SERVICE_NAME`, then the instance name.   |
| `otlpEndpoint`          | string  | No       | The OTLP `http/protobuf` endpoint base URL. Signal paths (`/v1/traces`, `/v1/metrics`) are appended.          |
| `otlpApiToken`          | string  | No       | API token sent as the `Authorization: Api-Token <token>` header (e.g. a Dynatrace token).                     |
| `exportIntervalSeconds` | int     | No       | Interval at which batched spans and metrics are exported. Defaults to `60`.                                   |

If telemetry is enabled but no `otlpEndpoint` is configured, NeonBee logs a warning and does not export the affected
signal.

The `service.name` reported to the backend is resolved in this order:

1. the configured `serviceName`,
2. the `OTEL_SERVICE_NAME` environment variable,
3. the NeonBee instance name (the "auto" default).

## Required JVM system property

The OpenTelemetry context must be propagated across Vert.x's asynchronous boundaries (including event bus / verticle
calls). This requires the Vert.x context storage provider to be selected via the
`io.opentelemetry.context.contextStorageProvider` system property:

```sh
java -Dio.opentelemetry.context.contextStorageProvider=io.vertx.tracing.opentelemetry.VertxContextStorageProvider \
     -jar neonbee.jar --enable-tracing
```

When tracing is enabled and this property is unset, NeonBee sets it programmatically during bootstrap and logs an
info message. Setting it explicitly as a JVM argument is still recommended, because it is guaranteed to be applied
before any OpenTelemetry context is used. If the property is already set to a different provider, NeonBee logs a
warning and leaves it unchanged; in that case parent/child span relationships across asynchronous calls may not be
linked correctly.

> **Note on configuration source and timing.** The tracer is attached to Vert.x *before* Vert.x is built, so the
> telemetry configuration must be known at that point. When NeonBee is started through its `Launcher` (the standard
> entry point), the `io.neonbee.NeonBee.yaml` `tracing` block is pre-loaded and bridged into the runtime options
> automatically, so the file configuration above works as documented. If you embed NeonBee programmatically via
> `NeonBee.create(NeonBeeOptions)` (without passing a pre-loaded `NeonBeeConfig`), set the `TracingConfig` directly on
> the (`Mutable`) options instead, as the config file is loaded only after Vert.x has already been created.

## Forwarding to Dynatrace

Dynatrace ingests OTLP over `http/protobuf` only (gRPC is not supported). Use the OTLP endpoint of your environment
and an API token with the appropriate scopes:

- Endpoint: `https://<environment-id>.live.dynatrace.com/api/v2/otlp` (or your Managed / ActiveGate URL).
- Token scopes: `openTelemetryTrace.ingest` for traces and `metrics.ingest` for metrics.

NeonBee appends the signal-specific paths (`/v1/traces`, `/v1/metrics`) automatically and exports metrics using
**delta** temporality, as required by Dynatrace.

## What is traced

With tracing enabled, Vert.x automatically instruments:

- inbound and outbound HTTP requests handled by the `ServerVerticle` and Vert.x HTTP clients, and
- **event bus / verticle communication** (e.g. `DataVerticle` and `EntityVerticle` request/reply), so a request can be
  followed across verticle boundaries.

The W3C Trace Context propagation format is used, so trace context is carried across HTTP and event bus messages.

## Metrics

When `exportMetrics` is enabled and an `otlpEndpoint` is configured, NeonBee attaches an OTLP Micrometer registry to
its `CompositeMeterRegistry`. All meters already collected for Prometheus (Vert.x, event bus, HTTP, pool and
`DataVerticle` metrics — see [Metrics Concept](./metrics.md)) are therefore also forwarded to the OTLP endpoint,
without any additional instrumentation.

The same registry can also be configured explicitly (independently of the `tracing` block) through the
`micrometerRegistries` array by referencing `io.neonbee.internal.tracing.OtlpMeterRegistryLoader` and providing an
`otlpEndpoint` (and optionally `otlpApiToken`, `exportIntervalSeconds`, `serviceName`) in its `config`.
