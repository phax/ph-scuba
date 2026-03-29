# Module Structure

ph-scuba follows the established ph-* Maven multi-module pattern.

## Modules

```
scuba (parent POM)
├── ph-scuba-api           Generic upload and content validation API (incl. SPI)
├── ph-scuba-core          Main business logic (upload pipeline)
└── ph-scuba-phive         Phive-specific content validators and VES upload logic
```

---

### ph-scuba-api

The API module defines interfaces and value types with **no implementation dependencies**.

**Key responsibilities:**
- Generic upload interface (`IScubaUploader`) - abstract contract for uploading artifacts
- **Content validation SPI** - a Java `ServiceLoader` interface (`IUploadContentValidatorSPI`) that allows modules to register content validators for specific file extensions
- **Content validator registry** (`IUploadContentValidatorRegistry`) - dispatch interface for validating content with context path support, enabling recursive/nested validation (e.g., ZIP entries)

**Content Validation SPI:**

The SPI mirrors the dispatch logic currently in `CentralUploader._isContentValid()`. Instead of a hard-coded `switch` on file extension, validators are discovered at runtime via `ServiceLoader`:

```java
// Defined in ph-scuba-api
public interface IUploadContentValidatorSPI
{
  // Which file extensions does this validator handle?
  // e.g. ".xsd", ".sch", ".xslt", ".zip", ".ves", ".status"
  ICommonsSet <String> getSupportedFileExtensions ();

  // Validate the content of a file with the given extension
  // Returns true if the content is valid for this file type
  boolean isValidContent (String sFileExt, InputStream aIS, ErrorList aErrorList) throws IOException;
}
```

The SPI creates a **reverse dependency** - `ph-scuba-api` defines the contract, implementations register via `META-INF/services` without any compile-time coupling from API to implementation:

```
ph-scuba-api                            ph-scuba-core
┌────────────────────────────┐          ┌─────────────────────────────────┐
│ IUploadContentValidatorSPI │◄── SPI ──│ XsdContentValidator             │
│                            │          │ SchContentValidator             │
│                            │          │ XsltContentValidator            │
│                            │          │ ZipContentValidator             │
└────────────────────────────┘          └─────────────────────────────────┘
                                          META-INF/services/
                                            com.helger.scuba.api.spi
                                              .IUploadContentValidatorSPI

                                        ph-scuba-phive
                                        ┌─────────────────────────────────┐
                                  SPI ──│ VesContentValidator             │
                                        │ VesStatusContentValidator       │
                                        └─────────────────────────────────┘
                                          META-INF/services/
                                            com.helger.scuba.api.spi
                                              .IUploadContentValidatorSPI
```

**Dependencies:**
- `ph-diver-api` - for `DVRCoordinate`, `DVRVersion`
- `ph-commons` - for base utilities (`IReadableResource`, `ICommonsSet`, etc.)

---

### ph-scuba-core

The core module provides the **main business logic** and default implementation of the upload pipeline.

**Key responsibilities:**
- Concrete uploader implementation (`ScubaUploader`) backed by `IRepoStorage`
- **`UploadContentValidator`** - a reusable, standalone class that loads all `IUploadContentValidatorSPI` implementations via `ServiceLoader` and dispatches validation by file extension. Supports nested context paths for hierarchical error reporting (e.g., validating files inside ZIP archives).
- Duplicate detection (reject if key already exists, throws `ScubaException`)

**Built-in content validators** (registered via SPI from this module):
- `.xsd` - XML Schema: XML well-formedness + root element `schema` in `http://www.w3.org/2001/XMLSchema` namespace
- `.sch` - Schematron: XML well-formedness check
- `.xslt` - XSLT: XML well-formedness + root element `stylesheet` in `http://www.w3.org/1999/XSL/Transform` namespace
- `.zip` - ZIP: entry integrity verification + **recursive content validation** of entries via `IUploadContentValidatorRegistry`

These are the generic validators from `CentralUploadValidator` that have **no phive dependency**.

**Dependencies:**
- `ph-scuba-api`
- `ph-diver-repo` - for `IRepoStorage`, `RepoStorageKeyOfArtefact`, ToC support
- `ph-xml` - for `DOMReader`, `XMLHelper` (XML well-formedness checks)

---

### ph-scuba-phive

Implements `IUploadContentValidatorSPI` for **phive-specific file types** and provides VES/VESStatus upload convenience logic.

This module exists as a separate submodule because the VES and VESStatus validation requires phive dependencies (`phive-ves-model`, `phive-ves-engine`) which should not be pulled into the generic core.

**Content validators** (registered via SPI):
- `.ves` - VES definition validation:
  - JAXB unmarshalling via `VES1Marshaller`
  - SPDX license ID validation (uniqueness + valid IDs)
  - License name cross-check
  - Eager requirement loading via `VESLoader` (all required artifacts must exist)
  - XSD catalog entry resolution
- `.status` - VES status validation:
  - JAXB unmarshalling via `VESStatus1Marshaller`
  - Deprecation consistency (reason only if deprecated flag is set)
  - Replacement VESID existence check

**VES upload convenience** (ported from `CentralUploader`):
- `addVES(VesType)` - marshal and upload VES XML
- `addVESStatus(VesStatusType)` - marshal and upload VESStatus XML
- `setVESDeprecated(DVRCoordinate, reason, replacement)` - read/update/write status
- `setVESValidityDate(DVRCoordinate, validFrom, validTo)` - read/update/write status
- UNIX newline normalization via `_getUnifiedMarshaller()` pattern

**Dependencies:**
- `ph-scuba-api` (SPI interface)
- `ph-scuba-core` (core upload logic)
- `phive-ves-model` - for `VES1Marshaller`, `VESStatus1Marshaller`, JAXB types (`VesType`, `VesStatusType`)
- `phive-ves-engine` - for `VESLoader`, `DefaultVESLoaderXSD`, `DefaultVESLoaderSchematron`
- `phive-api` - for `IValidationExecutorSetStatus`, `ValidationExecutorSetStatus`

---

## Dependency Graph

```
ph-diver-api ◄──── ph-scuba-api (defines IUploadContentValidatorSPI)
                        ▲
                        │
ph-diver-repo ◄─── ph-scuba-core (core: loads SPI, built-in .xsd/.sch/.xslt/.zip validators)
ph-xml ◄───────┘        ▲
                        │
                  ph-scuba-phive
                  (SPI: .ves/.status)
                        │
                        ▼
              phive-ves-model
              phive-ves-engine
              phive-api
```

## Maven Parent POM

The parent POM:
- Inherits from `com.helger:parent-pom:3.0.3`
- GroupId: `com.helger.scuba`
- Imports ph-diver, ph-commons, and phive BOMs via `<dependencyManagement>`
- Shared plugin configuration (compiler, surefire, etc.) is inherited from parent-pom
