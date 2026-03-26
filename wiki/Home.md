# ph-scuba - Secure Controlled Upload BAckend

ph-scuba is a generic uploader module for [diver](https://github.com/phax/ph-diver)-based repositories.
It provides a reusable, modular Java library for uploading versioned artifacts into DVR (Digitally Versioned Resources) repositories.

## Motivation

An internal project demonstrated the value of a structured upload pipeline for validation artifacts.
However, its upload logic is tightly coupled to the phive-central use case (VES definitions, Schematron rules, XSD schemas).

ph-scuba extracts and generalizes the upload mechanics so that **any** diver-based repository can benefit from:
- Validated, auditable uploads
- Content integrity verification (SHA-256 hashing)
- Table of Contents (ToC) management
- Support for multiple storage backends (local filesystem, HTTP, AWS S3)
- Consistent artifact coordinate handling via DVR Coordinates

## Wiki Contents

- [Architecture Overview](Architecture-Overview.md) - High-level design and module structure
- [Diver Concepts](Diver-Concepts.md) - DVR Coordinates, versions, and repository model
- [Module Structure](Module-Structure.md) - Maven modules and their responsibilities
- [Upload Workflow](Upload-Workflow.md) - How artifacts flow through the upload pipeline
- [Configuration](Configuration.md) - Storage backends and uploader configuration
- [Development Guide](Development-Guide.md) - Building, testing, and contributing

Note: ph-scuba is a library-only project. There is no standalone web application module.

## Related Projects

| Project | Description |
|---------|-------------|
| [ph-diver](https://github.com/phax/ph-diver) | DVR coordinate system and repository storage abstraction |
| [phive](https://github.com/phax/phive) | Philip Helger Integrative Validation Engine (VES, VESStatus) |
| [ph-commons](https://github.com/phax/ph-commons) | Common Java utilities used throughout the ph-* ecosystem |
