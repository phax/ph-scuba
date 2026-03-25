# Architecture Overview

## Design Goals

1. **Generic** - The core API (`ph-scuba-api`) is not tied to any specific artifact type
2. **Modular** - Consumers pick only the modules they need; phive integration is optional
3. **Extensible** - Content validators register per file extension via Java SPI; new storage backends plug in via ph-diver
4. **Safe** - Content validation before upload, integrity hashing, audit logging

## High-Level Architecture

```
                    ┌─────────────────────────────┐
                    │   ph-scuba-webapp            │
                    │   (Spring Boot 4.x REST API) │
                    └──────────┬──────────────────┘
                               │
                  ┌────────────┼────────────────┐
                  │                             │
                  ▼                             ▼
     ┌─────────────────────┐      ┌──────────────────────┐
     │   ph-scuba           │      │   ph-scuba-phive      │
     │   (Core upload logic,│      │   (VES/VESStatus      │
     │    SPI loading,      │      │    content validators  │
     │    .xsd/.sch/.xslt/  │      │    via SPI)           │
     │    .zip validators)  │      └──────────┬───────────┘
     └──────────┬──────────┘                  │
                │                             ▼
                ▼                  ┌──────────────────────┐
     ┌─────────────────────┐      │   phive-ves-model     │
     │   ph-scuba-api       │      │   phive-ves-engine    │
     │   (IUploader,        │      │   phive-api           │
     │    IUploadContent-   │      └──────────────────────┘
     │    ValidatorSPI)     │
     └──────────┬──────────┘
                │
                ▼
     ┌─────────────────────────────────────────┐
     │              ph-diver                    │
     │  (DVR Coordinates, Repository Storage)   │
     │  ┌──────────┬───────────┬──────────┐    │
     │  │   FS     │   HTTP    │   S3     │    │
     │  └──────────┴───────────┴──────────┘    │
     └─────────────────────────────────────────┘
```

## Content Validation via SPI

The central extensibility mechanism is the **`IUploadContentValidatorSPI`** interface defined in `ph-scuba-api`.

This replaces the hard-coded `switch` statement in `CentralUploader._isContentValid()` with a `ServiceLoader`-based discovery pattern:

1. `ph-scuba-api` defines the SPI interface
2. Modules register implementations via `META-INF/services`
3. `ph-scuba` (core) loads all SPI implementations at startup and dispatches validation by file extension
4. No compile-time dependency from API or core to specific validators

**Built-in validators** (in `ph-scuba`): `.xsd`, `.sch`, `.xslt`, `.zip`
**Phive validators** (in `ph-scuba-phive`): `.ves`, `.status`

## Relationship to ph-diver

SCUBA builds **on top of** ph-diver:

- **ph-diver** provides the storage abstraction (`IRepoStorage`), coordinate system (`DVRCoordinate`), and backend implementations.
- **SCUBA** adds the upload pipeline layer: content validation, pre-upload checks, audit logging, and a high-level upload API.

ph-diver owns "how to store", SCUBA owns "how to upload safely".

## Relationship to phive

The `ph-scuba-phive` module bridges SCUBA and phive:

- **phive** provides VES/VESStatus domain models, marshallers, and the validation engine.
- **ph-scuba-phive** registers `IUploadContentValidatorSPI` implementations for `.ves` and `.status` file types, and provides VES lifecycle convenience methods (deprecation, validity dates).

This replaces the tightly-coupled logic currently in `phive-central-tools` (`CentralUploader` / `CentralUploadValidator`) with a modular, SPI-based implementation.
