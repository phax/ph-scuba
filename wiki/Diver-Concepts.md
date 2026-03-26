# Diver Concepts

This page summarizes the key concepts from [ph-diver](https://github.com/phax/ph-diver) that ph-scuba relies on.

## DVR Coordinates

Every artifact in a diver repository is identified by a **DVR Coordinate** (Digitally Versioned Resource Coordinate), similar to Maven coordinates:

| Component | Required | Example |
|-----------|----------|---------|
| Group ID | Yes | `com.helger` |
| Artifact ID | Yes | `my-schema` |
| Version | Yes | `1.2.3` |
| Classifier | No | `sources` |

These coordinates determine the storage path:

```
{groupId as path}/{artifactId}/{version}/{artifactId}-{version}[-{classifier}].{ext}
```

Example: `com/helger/my-schema/1.2.3/my-schema-1.2.3.xsd`

## Versions

ph-diver supports two types of versions:

### Static Versions
Concrete version strings following a numeric dot-separated scheme:
- `1.0`, `2.3.1`, `1.0-SNAPSHOT`
- SNAPSHOTs sort before their release counterpart: `1.1-SNAPSHOT < 1.1`

### Pseudo Versions
Symbolic version references resolved at runtime:
- `latest` - Most recent version (including SNAPSHOTs)
- `latest-release` - Most recent non-SNAPSHOT version
- `oldest` - Oldest available version

## Repository Storage

ph-diver provides a pluggable storage abstraction:

### Storage Types

| Type | Class | Capabilities |
|------|-------|--------------|
| In-Memory | `RepoStorageInMemory` | Read, Write - for testing |
| Local Filesystem | `RepoStorageLocalFileSystem` | Read, Write, Delete |
| HTTP | `RepoStorageHttp` | Read, Write, Delete (via REST) |
| AWS S3 | `RepoStorageS3` | Read, Write, Delete |

### Storage Chain
Multiple storage backends can be chained for fallback resolution (e.g., local cache + remote server).
The chain tries each storage in order for reads and optionally caches remote content locally.

## Table of Contents (ToC)

Repositories maintain a Table of Contents that indexes:
- All available groups and artifacts (top-level ToC: `toptoc-diver.xml`)
- All versions per artifact (per-artifact ToC: `{group}/{artifact}/toc-diver.xml`)

The ToC is automatically updated via the `RepoTopTocAuditor` when artifacts are written.

## Integrity Verification

All artifacts can have an accompanying `.sha256` hash file.
On read, ph-diver verifies the content hash against the stored hash, ensuring data integrity.
