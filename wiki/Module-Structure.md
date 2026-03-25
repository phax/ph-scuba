# Module Structure

SCUBA follows the established ph-* Maven multi-module pattern.

## Modules

```
scuba (parent POM)
├── ph-scuba-api           Generic upload and validation API
├── ph-scuba               Main business logic
├── ph-scuba-phive         Phive-specific implementation (VES / VESStatus)
└── ph-scuba-webapp        Spring Boot 4.x standalone web application
```

---

### ph-scuba-api

The API module defines interfaces and value types with **no implementation dependencies**.

**Key responsibilities:**
- Generic upload interface (`IUploader`) - abstract contract for uploading artifacts
- Content validation API based on **filename extension** - validators register for specific extensions (e.g., `.xsd`, `.sch`, `.xml`, `.xslt`, `.zip`) and verify content matches the expected type
- Audit interface (`IUploadAuditor`) - upload event notifications
- Resource resolution interface (`IResourceResolver`) - resolving uploadable content
- **Java SPI (ServiceLoader) interface** for validator discovery - implementations in other modules (e.g., `ph-scuba-phive`) register themselves via `META-INF/services`, avoiding a compile-time dependency from API to implementation

**Java SPI pattern:**
```
ph-scuba-api                          ph-scuba-phive
┌──────────────────────┐              ┌──────────────────────────────┐
│ IUploadValidatorSPI  │◄── loaded ───│ PhiveUploadValidatorSPI      │
│ (ServiceLoader       │    via SPI   │ (registers .ves, .status,    │
│  interface)          │              │  .xsd, .sch, .xslt validators│)
└──────────────────────┘              └──────────────────────────────┘
                                       META-INF/services/
                                         com.helger.scuba.api.spi.IUploadValidatorSPI
```

The SPI interface allows `ph-scuba` (core) to discover and load validators from any module on the classpath without compile-time coupling.

**Dependencies:**
- `ph-diver-api` - for `DVRCoordinate`, `DVRVersion`
- `ph-commons` - for base utilities (`IReadableResource`, etc.)

---

### ph-scuba

The core module provides the **main business logic** and default implementation of the upload pipeline.

**Key responsibilities:**
- Concrete uploader implementation backed by `IRepoStorage`
- Content validation chain (dispatches to validators by file extension)
- SHA-256 integrity hash management
- ToC update coordination
- UNIX newline normalization for deterministic hashing
- Duplicate detection and overwrite policy

**Dependencies:**
- `ph-scuba-api`
- `ph-diver-repo` - for `IRepoStorage`, `RepoStorageKeyOfArtefact`, ToC support

---

### ph-scuba-phive

Implements the `ph-scuba-api` interfaces with **phive-engine integration** for VES and VESStatus artifacts.

**Key responsibilities:**
- Upload and validate **VES** (Validation Executor Set) definitions
  - Marshalling via `VES1Marshaller`
  - Structural validation of VES XML
  - Requirement resolution (verify referenced artifacts exist)
  - SPDX license validation
- Upload and manage **VESStatus** lifecycle
  - Marshalling via `VESStatus1Marshaller`
  - Deprecation management (reason, replacement VESID)
  - Validity date management (validFrom, validTo)
  - Status history tracking
- Extension-based validators for phive-specific file types:
  - `.ves` - VES definition files
  - `.status` - VES status files
  - `.xsd` - XML Schema files (well-formedness check)
  - `.sch` - Schematron files (well-formedness check)
  - `.xslt` - XSLT files (root element check)

**This module mirrors the logic currently in `phive-central-tools`** (`CentralUploader` / `ICentralUploader` / `CentralUploadValidator`) but implements it against the generic `ph-scuba-api` interfaces.

**Dependencies:**
- `ph-scuba-api`
- `ph-scuba` (core upload logic)
- `phive-ves-model` - for `VES1Marshaller`, `VESStatus1Marshaller`, JAXB types
- `phive-ves-engine` - for `VESLoader`, validation execution
- `phive-api` - for `IValidationExecutorSet`, `IValidationExecutorSetStatus`

---

### ph-scuba-webapp

A standalone **Spring Boot 4.x** web application providing an HTTP interface to the upload functionality.

**Pattern:** Follows the same structure as `phoss-ap-webapp` in the [phoss-ap](https://github.com/phax/phoss-ap) project.

**Key responsibilities:**
- REST API for upload operations
- Web-based upload interface (optional)
- Configuration via `application.properties`
- Spring Boot Actuator for health/monitoring
- API security (token-based authentication)

**Spring Boot setup:**
- Spring Boot 4.x with embedded server
- `@SpringBootApplication` main class
- Executable JAR packaging via `spring-boot-maven-plugin`
- Profile-based configuration (`application.properties`, `application-private.properties`)

**Dependencies:**
- `ph-scuba` (core business logic)
- `ph-scuba-phive` (phive-specific upload support)
- `spring-boot-starter-web`
- `spring-boot-starter-actuator`
- Storage backend dependency as needed (`ph-diver-repo-http`, `ph-diver-repo-s3`)

---

## Dependency Graph

```
ph-diver-api ◄──── ph-scuba-api
                        ▲
                        │
ph-diver-repo ◄─── ph-scuba (core)
                        ▲
                        │
                   ┌────┴──────────────┐
                   │                   │
            ph-scuba-phive      ph-scuba-webapp
                   │                   │
                   ▼                   │
         phive-ves-model               │
         phive-ves-engine              │
         phive-api                     │
                                       ▼
                              Spring Boot 4.x
                              ph-diver-repo-http / s3
```

## Maven Parent POM

The parent POM will:
- Inherit from `com.helger:parent-pom` for consistent build settings
- Import ph-diver and ph-commons BOMs via `<dependencyManagement>`
- Import phive BOM for the ph-scuba-phive module
- Import Spring Boot BOM for the webapp module
- Define shared plugin configuration (compiler, surefire, etc.)
