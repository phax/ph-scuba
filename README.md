# ph-scuba

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger.scuba/ph-scuba-parent-pom/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger.scuba/ph-scuba-parent-pom/)
[![javadoc](https://javadoc.io/badge2/com.helger.scuba/ph-scuba-api/javadoc.svg)](https://javadoc.io/doc/com.helger.scuba/ph-scuba-api)
<!-- ph-badge-end -->

SCUBA - **S**ecure **C**ontrolled **U**pload **BA**ckend

A generic, modular Java library for uploading versioned artifacts into [ph-diver](https://github.com/phax/ph-diver)-based (DVR - Digitally Versioned Resources) repositories.

ph-scuba provides a reusable upload pipeline with pluggable content validation, integrity verification, and support for multiple storage backends.

## Motivation

An internal project demonstrated the value of a structured upload pipeline for validation artifacts.
However, its upload logic was tightly coupled to the phive-central use case (VES definitions, Schematron rules, XSD schemas).

ph-scuba extracts and generalizes the upload mechanics so that **any** diver-based repository can benefit from:
- Validated, auditable uploads with pluggable content validation via Java SPI
- Recursive content validation (e.g., validating files inside ZIP archives)
- Content integrity verification (SHA-256 hashing)
- Table of Contents (ToC) management
- Support for multiple storage backends (local filesystem, HTTP, AWS S3)
- Consistent artifact coordinate handling via [DVR Coordinates](https://github.com/phax/ph-diver#dvr-coordinate)

## Modules

This library consists of the following submodules:
* **`ph-scuba-api`** - contains the generic upload interface (`IScubaUploader`), the content validation SPI (`IUploadContentValidatorSPI`), the validation registry interface (`IUploadContentValidatorRegistry`), and repository exception classes. This module has no implementation dependencies.
* **`ph-scuba-core`** - contains the main upload pipeline implementation (`ScubaUploader`), the content validator dispatcher (`UploadContentValidator`), uploader settings (`ScubaUploaderSettings`), and built-in content validators for common file types (`.xsd`, `.sch`, `.xslt`, `.zip`).
* **`ph-scuba-phive`** - contains [phive](https://github.com/phax/phive)-specific content validators for VES (`.ves`) and VES Status (`.status`) files, the phive upload convenience class (`PhiveUploader`) for VES lifecycle management, and the SPDX license validation helper. This module is optional - only needed when uploading phive validation artifacts.

The reason why phive-specific support is in a separate module is to avoid pulling in phive dependencies when they are not needed.

## Content Validation via SPI

The central extensibility mechanism is the `IUploadContentValidatorSPI` interface defined in `ph-scuba-api`.

Content validators register for specific file extensions and are discovered at runtime via `java.util.ServiceLoader`.
The `UploadContentValidator` in `ph-scuba-core` loads all SPI implementations and dispatches validation by file extension.

### Built-in Validators (ph-scuba-core)

| Extension | Validator | What it checks |
|-----------|-----------|----------------|
| `.xsd` | `XsdContentValidator` | XML well-formedness, root element `schema` in W3C XSD namespace |
| `.sch` | `SchContentValidator` | XML well-formedness |
| `.xslt` | `XsltContentValidator` | XML well-formedness, root element `stylesheet` in XSL Transform namespace |
| `.zip` | `ZipContentValidator` | ZIP entry integrity + recursive content validation of entries |

### Phive Validators (ph-scuba-phive)

| Extension | Validator | What it checks |
|-----------|-----------|----------------|
| `.ves` | `VesContentValidator` | JAXB unmarshalling, SPDX license validation, requirement resolution, XSD catalog checks |
| `.status` | `VesStatusContentValidator` | JAXB unmarshalling, deprecation consistency, replacement VESID existence |

### Custom Validators

To add validation for a custom file type:
1. Implement `IUploadContentValidatorSPI` in your module
2. Register it in `META-INF/services/com.helger.scuba.api.spi.IUploadContentValidatorSPI`
3. Add the module to the classpath - the validator is automatically discovered

### Recursive Validation

The `ZipContentValidator` recursively validates entries inside ZIP archives using the `IUploadContentValidatorRegistry`.
Error messages from nested validation carry the full context path (e.g., `archive.zip/schemas/my-schema.xsd: The root element ...`).
Nesting is fully flexible - a ZIP inside a ZIP produces multi-level context paths.

## Uploader Settings

The `ScubaUploader` accepts a `ScubaUploaderSettings` object with configurable behavior:

| Setting | Default | Description |
|---------|---------|-------------|
| `allowUploadWithUnknownExtension` | `false` | Whether artifacts with unknown file extensions (no SPI validator registered) are allowed to be uploaded without content validation |
| `allowOverwriteExisting` | `false` | Whether existing artifacts may be overwritten |

Example:
```java
ScubaUploaderSettings settings = new ScubaUploaderSettings ()
    .setAllowUploadWithUnknownExtension (true)
    .setAllowOverwriteExisting (false);
ScubaUploader uploader = new ScubaUploader (repo, settings);
```

## Maven Usage

Add the following to your `pom.xml` to use the core upload artifact, replacing `x.y.z` with the latest version:

```xml
<dependency>
  <groupId>com.helger.scuba</groupId>
  <artifactId>ph-scuba-core</artifactId>
  <version>x.y.z</version>
</dependency>
```

For phive VES/VESStatus support, also add:

```xml
<dependency>
  <groupId>com.helger.scuba</groupId>
  <artifactId>ph-scuba-phive</artifactId>
  <version>x.y.z</version>
</dependency>
```

Alternate usage as a Maven BOM:

```xml
<dependency>
  <groupId>com.helger.scuba</groupId>
  <artifactId>scuba-parent-pom</artifactId>
  <version>x.y.z</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
```

## Building from Source

Prerequisites:
- Java 17+
- Apache Maven 3.9+

```bash
mvn clean install
```

## Related Projects

| Project | Description |
|---------|-------------|
| [ph-diver](https://github.com/phax/ph-diver) | DVR coordinate system and repository storage abstraction |
| [phive](https://github.com/phax/phive) | Philip Helger Integrative Validation Engine (VES, VESStatus) |
| [ph-commons](https://github.com/phax/ph-commons) | Common Java utilities used throughout the ph-* ecosystem |

## News and Noteworthy

v1.0.0 - 2026-03-27
* Initial version
* Extracted and generalized upload logic from an internal tool
* Content validation via Java SPI with recursive ZIP support
* Built-in validators for XSD, Schematron, XSLT, and ZIP
* phive-specific validators for VES and VESStatus
* Configurable uploader settings (unknown extension handling, overwrite policy)

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.