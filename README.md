# ph-scuba

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger.scuba/ph-scuba-parent-pom/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger.scuba/ph-scuba-parent-pom/)
[![javadoc](https://javadoc.io/badge2/com.helger.scuba/ph-scuba-api/javadoc.svg)](https://javadoc.io/doc/com.helger.scuba/ph-scuba-api)

> If this project saved you some time or made your day a little easier, a star would mean a lot — it helps others find it too.
<!-- ph-badge-end -->

SCUBA - **S**ecure **C**ontrolled **U**pload **BA**ckend

A generic, modular Java library for uploading versioned artifacts into [ph-diver](https://github.com/phax/ph-diver)-based (DVR - Digitally Versioned Resources) repositories.

ph-scuba provides a reusable upload pipeline with pluggable content validation, integrity verification, and support for multiple storage backends.

The detailed documentation is in the [ph-scuba wiki](https://github.com/phax/ph-scuba/wiki).

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
* **`ph-scuba-bom`** - the Bill of Materials (BOM) that contains the versions of all ph-scuba artifacts for use in `<dependencyManagement>`.
* **`ph-scuba-api`** - contains the generic upload interface (`IScubaUploader`), the content validation SPI (`IUploadContentValidatorSPI`), the validation registry interface (`IUploadContentValidatorRegistry`), and repository exception classes. This module has no implementation dependencies.
* **`ph-scuba-core`** - contains the main upload pipeline implementation (`ScubaUploader`), the content validator dispatcher (`UploadContentValidator`), uploader settings (`ScubaUploaderSettings`), and built-in content validators for common file types (`.xsd`, `.sch`, `.xslt`, `.xsl`, `.zip`).
* **`ph-scuba-phive`** - contains [phive](https://github.com/phax/phive)-specific content validators for VES (`.ves`) and VES Status (`.status`) files, the phive upload convenience class (`PhiveUploader`) for VES lifecycle management, and the SPDX license validation helper. This module is optional - only needed when uploading phive validation artifacts.
* **`ph-scuba-cli`** - the command line client to check and upload artefacts, manage the status of VES artefacts and determine their latest versions. It is built as an executable JAR and as the Docker image `phelger/scuba`. See the [Command Line Client](https://github.com/phax/ph-scuba/wiki/Command-Line-Client) wiki page.

The reason why phive-specific support is in a separate module is to avoid pulling in phive dependencies when they are not needed.

## Content Validation via SPI

The central extensibility mechanism is the `IUploadContentValidatorSPI` interface defined in `ph-scuba-api`.

Content validators register for specific file extensions and are discovered at runtime via `java.util.ServiceLoader`.
The `UploadContentValidator` in `ph-scuba-core` loads all SPI implementations and dispatches validation by file extension.

### Built-in Validators (ph-scuba-core)

| Extension | Validator | What it checks |
|-----------|-----------|----------------|
| `.xsd` | `XsdContentValidator` | XML well-formedness, root element `schema` in W3C XSD namespace. Like phive, a DOCTYPE with an internal subset is allowed, but nothing external is loaded |
| `.sch` | `SchContentValidator` | XML well-formedness, valid Schematron structure |
| `.xslt`, `.xsl` | `XsltContentValidator` | XML well-formedness, root element `stylesheet` or `transform` in XSL Transform namespace |
| `.zip` | `ZipContentValidator` | ZIP header, ZIP entry integrity + recursive content validation of entries, with limits for nesting depth, entry count and total uncompressed size |

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
Error messages from nested validation carry the path inside the archive (e.g., `zip:schemas/my-schema.xsd: The root element ...`).
A ZIP inside a ZIP is walked as well, with the paths separated by `/` (e.g., `zip:inner.zip/schemas/my-schema.xsd: ...`), up to the configured nesting depth.

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

Alternate usage as a Maven BOM (put it in the `<dependencyManagement>` section and omit the versions of the ph-scuba dependencies):

```xml
<dependency>
  <groupId>com.helger.scuba</groupId>
  <artifactId>ph-scuba-bom</artifactId>
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

v1.2.0 - 2026-11-28
* **Security: the Docker image `phelger/scuba` disables the HotSpot Base64 intrinsic (`-XX:-UseBASE64Intrinsics`) as mitigation for CVE-2026-114938 ("Java2Shell").**
* Requires at least ph-diver 4.4.0 and ph-commons 12.7.2
* Added new submodule `ph-scuba-cli` with a command line client based on picocli, including a Docker setup (`phelger/scuba`)
* Added class `CScubaVersion` in `ph-scuba-api` with the ph-scuba version and build timestamp
* The command line client prints a headline with product name and version at the start of each command, except in quiet mode, and as header of the help output
* The command line client supports the repository types `localfs`, `http` and `aws-s3` from a ph-diver repository configuration.
  Secrets of HTTP repositories are resolved via ph-config (system properties, environment variables, application properties files)
* Added `PhiveUploader.setAuthor` to record the real author in the VES status history
* Added `PhiveUploader.setFailOnStatusHashMismatch` to refuse modifying a VES status whose hash value does not match (disabled by default)
* `PhiveUploader.setVESDeprecated` and `setVESValidityDate` now check the modified status like an uploaded `.status` file before writing it, so a replacement VESID must exist in the repository
* `PhiveUploader.setVESDeprecated` and `setVESValidityDate` now throw an `IllegalStateException` instead of returning `EChange.UNCHANGED` if the status cannot be serialized, checked or written
* `PhiveUploader.setVESDeprecated` and `setVESValidityDate` now require the VES to exist in the repository
* `PhiveUploader.setVESValidityDate` now also checks a single provided date against the other date of the existing status
* `PhiveUploader.setFailOnStatusHashMismatch (true)` now also refuses a status whose hash value cannot be verified (e.g. missing `.sha256` file)
* `ScubaUploader` now reads the payload once, so that the checked content is exactly the uploaded content.
  This also fixes a `NullPointerException` for resources that are not files
* `ScubaUploader` now throws a `ScubaException` instead of an `IllegalStateException` / `IllegalArgumentException` for invalid content and unsupported file extensions, and an `IOException` if writing fails
* `ScubaUploader` now refuses resources without a file extension
* `ScubaUploader.deleteResource` now throws an `IOException` if deleting fails, and `existsResource` adds a missing leading dot of the file extension
* The input streams passed to content validators are now closed by the caller, as documented in `IUploadContentValidatorSPI`
* File extensions are now matched case-insensitively; a warning is logged if two content validators register the same file extension
* The `.zip` content validator now uses `ZipWalker` from ph-commons: it refuses content that is not a ZIP archive and limits the nesting depth (10), the number of entries (100,000) and the total uncompressed size (1 GiB).
  The limits can be changed with `ZipContentValidator.setSettings (ZipWalkerSettings)`
* The `.xsd` content validator now accepts a DOCTYPE with an internal subset (e.g. `UBL-xmldsig-core-schema-2.1.xsd` of the UBL 2.1 XSD ZIP), like phive does when loading XSDs.
  External DTDs and external entities are still never loaded, and the entity expansion is limited
* The `.sch` content validator now also checks the Schematron structure, not only the well-formedness.
  Included files (`sch:include`) are not resolved: their content is not checked (a warning is reported) and a Schematron with includes does not need to contain a pattern itself
* The XSLT content validator now also handles the file extension `.xsl` and the root element `transform`
* The `.ves` and `.status` content validators now report invalid or pseudo versions of referenced artefacts as errors instead of throwing an exception
* The `.ves` content validator now compares the SPDX ID with the IDs of the licence name case-insensitively
* `SPDXHelper.getAllSpdxIDsOfName` now returns a copy
* The command line client has the new command `versions` to list all versions of an artefact, the option `--dry-run` for `upload`, `ves add` and `ves add-status`, and the command `generate-completion` for a bash/zsh completion script
* The command line client never logs HTTP headers, so that credentials do not end up in the log
* Requires phive 12.4.0 (for the requirement resolver)
* Added `PhiveUploader.setVESNotDeprecated` and the command line command `ves undeprecate` to revert a deprecation.
  The deprecation reason and the replacement are removed.
  See [issue #2](https://github.com/phax/ph-scuba/issues/2)
* Added `VesContentValidatorSPI.setRequirementResolver` (and the shortcut `PhiveUploader.setRequirementResolver`): a `requires` or `partialRequires` of an uploaded VES that is not in the target repository can be resolved by the caller, e.g. to a rule set built into the application.
  By default such a requirement is still refused; the command line client keeps the default.
  See [issue #3](https://github.com/phax/ph-scuba/issues/3)
* Added `UploadContentValidator.getValidatorOfClass` to access a loaded content validator, e.g. to configure it

v1.1.1 - 2026-11-15
* Added new submodule `ph-scuba-bom` as a dedicated Maven BOM.
  It should be used instead of `ph-scuba-parent-pom` for importing

v1.1.0 - 2026-07-17
* Updated to ph-schematron v10.x

v1.0.2 - 2026-04-09
* Improved Schematron consistency check

v1.0.1 - 2026-03-31
* Improved `PhiveUploader` resilience and error handling
* Avoided NPE and added descriptive error messages when writing fails
* Reduced logging verbosity when reading invalid VES/VESStatus data

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
