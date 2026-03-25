# Upload Workflow

This page describes how artifacts flow through the SCUBA upload pipeline.

## Overview

```
   Resource              Validate             Store
  ┌────────┐           ┌─────────┐          ┌───────────┐
  │ Input  │──resolve──│  Check   │──write──▶│  Repo     │
  │ File   │           │  Content │          │  Storage  │
  └────────┘           └─────────┘          └───────────┘
       │                    │                     │
       ▼                    ▼                     ▼
  DVRCoordinate       Extension-based        SHA-256 hash
  + file extension    validator dispatch     + ToC update
```

## Step-by-Step

### 1. Identify the Artifact

The caller provides:
- A `DVRCoordinate` (group ID, artifact ID, version, optional classifier)
- The file extension (e.g., `.xsd`, `.xml`, `.sch`, `.ves`)
- The content to upload (as `IReadableResource` or byte array)

### 2. Validate Content (Pre-Upload)

The upload pipeline selects validators based on the **file extension** of the artifact being uploaded.
Each `IUploadValidator` registers for one or more extensions it can handle.

**Generic validators** (in `ph-scuba`):
- **Duplicate check** - does an artifact with this coordinate already exist?
- **Size limit check** - is the content within acceptable bounds?

**Phive-specific validators** (in `ph-scuba-phive`):
- `.xsd` - XML Schema well-formedness check
- `.sch` - Schematron well-formedness check
- `.xslt` - XSLT root element verification
- `.ves` - VES structural validation, SPDX license check, requirement resolution
- `.status` - VESStatus structural validation
- `.zip` - ZIP entry integrity check

Custom validators can be registered for any extension.

### 3. Write to Repository

If validation passes:
1. The content is wrapped in an `IRepoStorageContent`
2. A `RepoStorageKeyOfArtefact` is derived from the coordinate and file extension
3. `IRepoStorage.write(key, content)` persists the artifact
4. A `.sha256` hash file is written alongside the artifact
5. The repository ToC is updated (via `RepoTopTocAuditor`)

### 4. Audit

Upload events are dispatched to registered `IUploadAuditor` instances:
- Upload started (coordinate, timestamp)
- Upload succeeded / failed (result, duration)

This enables logging, metrics, or external notification.

## VES-Specific Workflows (ph-scuba-phive)

### Upload VES

1. Create `VesType` JAXB object with groupId, artifactId, version, name
2. Add licenses (validated against SPDX database)
3. Add requirements (referenced artifacts that must exist in repository)
4. Add schematrons/XSDs (validation rules)
5. Marshal to XML using `VES1Marshaller` (with UNIX newline normalization)
6. Upload via generic pipeline (extension `.ves`)

### Upload VESStatus

1. Create `VesStatusType` JAXB object
2. Set deprecation info, validity dates, history items
3. Marshal to XML using `VESStatus1Marshaller`
4. Upload via generic pipeline (extension `.status`)

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
| Validate | Content invalid for extension | Upload rejected, error details returned |
| Write | Storage failure | Exception, no partial state |
| Write | Duplicate key | Configurable: reject or overwrite |
