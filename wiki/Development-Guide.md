# Development Guide

## Prerequisites

- **Java 17+** (consistent with phoss-ap and current ph-* ecosystem)
- **Apache Maven 3.9+**
- **Git**

## Building

```bash
mvn clean install
```

## Project Conventions

ph-scuba follows the conventions established across the ph-* ecosystem:

### Code Style
- Package root: `com.helger.scuba`
- Interfaces prefixed with `I` (e.g., `IScubaUploader`)
- Constants classes prefixed with `C` (e.g., `CScuba`)
- Enum classes prefixed with `E` (e.g., `EUploadResult`)
- Null safety via JSpecify annotations (`@NonNull`, `@Nullable`)

### Maven
- Parent POM: `com.helger:parent-pom`
- BOM imports for transitive dependency management
- JAXB plugin for XML binding where needed

### Testing
- JUnit 5
- SLF4J Simple for test logging
- In-memory storage (`RepoStorageInMemory`) for fast, isolated tests

## Module Dependencies

When adding a new module:
1. Add it to the parent POM `<modules>` section
2. Define its dependencies in the parent POM `<dependencyManagement>` section
3. Keep the dependency graph acyclic and layered (api -> core -> phive)

## Reference Projects

| Project | What to reference |
|---------|-------------------|
| phive-central-tools | VES/VESStatus upload logic (being generalized into ph-scuba-phive) |
| ph-diver | Repository storage abstraction, DVR coordinates |
| phive | VES model, marshallers, validation engine |
