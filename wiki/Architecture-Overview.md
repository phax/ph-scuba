# Architecture Overview

## Design Goals

1. **Generic** - The core API (`ph-scuba-api`) is not tied to any specific artifact type
2. **Modular** - Consumers pick only the modules they need; phive integration is optional
3. **Extensible** - Custom validators register by file extension; new storage backends plug in via ph-diver
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
     │   (Core upload logic)│◄─────│   (VES/VESStatus      │
     └──────────┬──────────┘      │    integration)        │
                │                  └──────────┬───────────┘
                │                             │
                ▼                             ▼
     ┌─────────────────────┐      ┌──────────────────────┐
     │   ph-scuba-api       │      │   phive-ves-model     │
     │   (Generic upload &  │      │   phive-ves-engine    │
     │    validation API)   │      │   phive-api           │
     └──────────┬──────────┘      └──────────────────────┘
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

## Relationship to ph-diver

SCUBA builds **on top of** ph-diver rather than forking or wrapping it:

- **ph-diver** provides the storage abstraction (`IRepoStorage`), coordinate system (`DVRCoordinate`), and backend implementations.
- **SCUBA** adds the upload pipeline layer: content validation, pre-upload checks, audit logging, and a high-level upload API.

This keeps the layering clean - ph-diver owns "how to store", SCUBA owns "how to upload safely".

## Relationship to phive

The `ph-scuba-phive` module bridges SCUBA and phive:

- **phive** provides VES/VESStatus domain models, marshallers, and the validation engine.
- **ph-scuba-phive** implements `ph-scuba-api` validators for phive-specific file types (`.ves`, `.status`, `.xsd`, `.sch`, `.xslt`) and provides upload logic for VES lifecycle management (deprecation, validity dates, status history).

This replaces the tightly-coupled logic currently in `phive-central-tools` (`CentralUploader`) with a modular, reusable implementation.

## Key Interfaces (in ph-scuba-api)

| Interface | Purpose |
|-----------|---------|
| `IUploader` | Main upload entry point |
| `IUploadValidator` | Validates content before upload, registered per file extension |
| `IUploadAuditor` | Receives notifications about upload events |
| `IResourceResolver` | Resolves resources for upload |
