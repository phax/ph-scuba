# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
mvn clean install          # Full build with tests
mvn clean compile          # Compile only (fast check)
mvn clean test             # Compile + run tests
mvn test -pl ph-scuba-core # Run tests for a single module
mvn test -pl ph-scuba-core -Dtest=XsdContentValidatorTest  # Run a single test class
```

Java 17+ and Maven 3.9+ required. No linter or formatter configured separately - formatting is enforced by the parent POM (`com.helger:parent-pom`).

## Spelling

Use lowercase "scuba" or "ph-scuba" everywhere in prose, Javadoc, POM descriptions, and wiki. Uppercase "SCUBA" is only used once at the top of the README as the abbreviation expansion.

## Code Conventions (ph-* ecosystem)

- **Package root:** `com.helger.scuba`
- **Naming prefixes:** `I` for interfaces, `C` for constants classes, `E` for enums
- **Null safety:** JSpecify annotations (`@NonNull`, `@Nullable`)
- **SPI annotations:** `@IsSPIInterface` on SPI interfaces, `@IsSPIImplementation` on implementations
- **Formatting:** Space before parentheses in method calls/declarations: `foo ()`, `new Bar ()`, `if (x)`. Space inside generics: `ICommonsList <String>`.
- **Settings/builder pattern:** Mutable class with chainable setters returning `this` (see `ScubaUploaderSettings` or `HttpRetrySettings` in phase4 for reference). Include `assignFrom()`, `getClone()`, `equals()`, `hashCode()`, `toString()`.
- **All classes** must have Javadoc with `@author Philip Helger`.
- **License header:** Apache 2.0, copyright 2026.

## Architecture

ph-scuba is a multi-module Maven project for uploading artifacts to ph-diver repositories.

**Dependency flow:** `ph-scuba-api` <- `ph-scuba-core` <- `ph-scuba-phive` <- `ph-scuba-cli`

### ph-scuba-bom
Bill of Materials listing all ph-scuba library artifacts with `${project.version}`. Consumers import it instead of `ph-scuba-parent-pom`. When adding a new library module, add it to this BOM as well. `ph-scuba-cli` is an application and deliberately not part of the BOM.

### ph-scuba-api
Pure interfaces and exceptions. Defines `IScubaUploader` (upload contract), `IUploadContentValidatorSPI` (Java ServiceLoader interface for file-extension-based content validation), and `IUploadContentValidatorRegistry` (dispatch interface with context path support for recursive validation).

### ph-scuba-core
Upload pipeline implementation. `ScubaUploader` delegates content validation to `UploadContentValidator`, which loads all `IUploadContentValidatorSPI` implementations via ServiceLoader and dispatches by file extension. Built-in validators: `.xsd`, `.sch`, `.xslt`, `.zip`. The ZIP validator recursively validates entries via `IUploadContentValidatorRegistry`, prefixing error messages with context paths.

SPI registration: `src/main/resources/META-INF/services/com.helger.scuba.api.spi.IUploadContentValidatorSPI`

### ph-scuba-phive
Optional phive-specific module. Provides SPI validators for `.ves` and `.status` files (require phive dependencies) and `PhiveUploader` for VES lifecycle management (addVES, addVESStatus, setVESDeprecated, setVESValidityDate). Separated to avoid pulling phive dependencies when not needed. `PhiveUploader` has a configurable history author (`setAuthor`) and an opt-in `setFailOnStatusHashMismatch` (off by default, not enabled by the CLI); status modifications are checked by the `.status` validator before writing and throw `IllegalStateException` on failure.

### ph-scuba-cli
picocli based command line client (`com.helger.scuba.cli.ScubaCli`), packaged as shaded JAR and Docker image `phelger/scuba` (`ph-scuba-cli/Dockerfile`, `docker/build-all.sh` - bump `version` there on release). Repository selection via `--basedir` (localfs, must exist) or `--config` + `--repo` (ph-diver-repo-config). Commands: `upload`, `validate`, `exists`, `ves add|add-status|deprecate|validity|latest|latest-release|latest-active`. Exit codes are defined in `ScubaCli` (0 ok, 1 failure, 2 usage, 3 technical error, 4 conflict). Tests run the client in-process via `CliRunner`. Planned: HTTP/S3 repository factories (phase 2), artefact signing (phase 3). Documented in `wiki/Command-Line-Client.md`.

### Key external dependencies
- **ph-diver** (`com.helger.diver`): DVR coordinates, repository storage abstraction (`IRepoStorageWithToc`)
- **phive** (`com.helger.phive`): VES/VESStatus model and engine (only in ph-scuba-phive)
- **ph-commons** (`com.helger.commons`): Base utilities, error handling (`ErrorList`, `SingleError`), XML processing

## Wiki

The `wiki/` folder contains design documentation. Update it when making material changes to module structure or architecture.
