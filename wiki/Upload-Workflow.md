# Upload Workflow

This page describes how artifacts flow through the SCUBA upload pipeline.

## Overview

```
   Resource              Validate             Store
  ┌────────┐           ┌─────────┐          ┌───────────┐
  │ Input  │──resolve──│  SPI     │──write──▶│  Repo     │
  │ File   │           │  Check   │          │  Storage  │
  └────────┘           └─────────┘          └───────────┘
       │                    │                     │
       ▼                    ▼                     ▼
  DVRCoordinate       ServiceLoader          SHA-256 hash
  + file extension    dispatches by ext      + ToC update
```

## Step-by-Step

### 1. Identify the Artifact

The caller provides:
- A `DVRCoordinate` (group ID, artifact ID, version, optional classifier)
- The content to upload (as `IReadableResource` or byte array)
- The file extension is extracted from the resource path (e.g., `.xsd`, `.ves`, `.sch`)

### 2. Validate Content (Pre-Upload)

The core module (`ph-scuba`) loads all `IUploadContentValidatorSPI` implementations via `ServiceLoader` and finds the validator that handles the artifact's file extension.

**Validator dispatch** (replaces the `switch` in `CentralUploader._isContentValid()`):

| Extension | Validator | Module | What it checks |
|-----------|-----------|--------|----------------|
| `.xsd` | `XsdContentValidator` | ph-scuba | XML well-formedness, root element `schema` in W3C XSD namespace |
| `.sch` | `SchContentValidator` | ph-scuba | XML well-formedness |
| `.xslt` | `XsltContentValidator` | ph-scuba | XML well-formedness, root element `stylesheet` in XSL Transform namespace |
| `.zip` | `ZipContentValidator` | ph-scuba | ZIP entry integrity (all entries readable) |
| `.ves` | `VesContentValidator` | ph-scuba-phive | JAXB unmarshalling, SPDX license validation, requirement resolution, XSD catalog checks |
| `.status` | `VesStatusContentValidator` | ph-scuba-phive | JAXB unmarshalling, deprecation consistency, replacement VESID existence |

If no validator is registered for a file extension, the upload is rejected with an error.

### 3. Check for Duplicates

After content validation passes, the core checks whether a resource with the same `RepoStorageKeyOfArtefact` already exists in the repository. If so, the upload is rejected (`RepoKeyAlreadyInUseException`).

### 4. Write to Repository

1. The content is wrapped in an `IRepoStorageContent`
2. A `RepoStorageKeyOfArtefact` is derived from the coordinate and file extension
3. `IRepoStorage.write(key, content)` persists the artifact
4. A `.sha256` hash file is written alongside the artifact
5. The repository ToC is updated (via `RepoTopTocAuditor`)

## VES-Specific Workflows (ph-scuba-phive)

### Upload VES

1. Create `VesType` JAXB object with groupId, artifactId, version, name
2. Add licenses (validated against SPDX database)
3. Add requirements (referenced artifacts that must exist in repository)
4. Add schematrons/XSDs (validation rules)
5. Marshal to XML using `VES1Marshaller` with UNIX newline normalization
6. Upload via generic pipeline (extension `.ves`, content validated by `VesContentValidator` SPI)

### Upload VESStatus

1. Create `VesStatusType` JAXB object
2. Set deprecation info, validity dates, history items
3. Marshal to XML using `VESStatus1Marshaller` with UNIX newline normalization
4. Upload via generic pipeline (extension `.status`, content validated by `VesStatusContentValidator` SPI)

### Deprecate a VES

1. Read existing VESStatus from repository (or create new)
2. Set deprecated flag, reason, and optional replacement VESID
3. Record history item with timestamp and author
4. Write updated VESStatus back

## Newline Normalization

XML content is normalized to UNIX newlines (`\n`) before upload.
This ensures consistent SHA-256 hashes across platforms, since the hash is computed on the stored bytes.

## Error Handling

Uploads can fail at multiple stages:

| Stage | Error | Behavior |
|-------|-------|----------|
| Resolve | Resource not found | Exception before validation |
| Validate | No SPI validator for extension | Upload rejected |
| Validate | Content invalid for extension | Upload rejected, error logged |
| Write | Duplicate key | `RepoKeyAlreadyInUseException` |
| Write | Storage failure | Exception, no partial state |
