# Command Line Client

The module `ph-scuba-cli` provides the executable JAR `ph-scuba-cli-<version>-shaded.jar` and the Docker image `phelger/scuba`.
It wraps `ScubaUploader` and `PhiveUploader`, so an upload from the command line runs through the same content checks as an upload via the API.

```bash
java -jar ph-scuba-cli-<version>-shaded.jar <command> [options]
```

## Selecting the Repository

Every command that works on a repository needs exactly one of:

| Option | Meaning |
|--------|---------|
| `--basedir DIR` | A local file system repository (type `localfs`). The directory must already exist - a typo never creates a new, empty repository. |
| `--toptoc TYPE` | Only with `--basedir`: the top-level ToC type, `none` (default) or `diver-xml`. |
| `--config FILE --repo ID` | The repository with the given ID from a ph-diver repository configuration file (namespace `urn:fdc:com:helger:diver:repoconfig:v1.0`). Only this repository is created. Commands that write require `writable="true"` in the configuration. |

At the moment the only repository type in the configuration is `localfs`. HTTP and S3 are planned for phase 2.

Hash values are always verified when the repository is read.

## Commands

| Command | Purpose |
|---------|---------|
| `upload COORDINATE FILE` | Check a file based on its file extension and upload it to `groupID:artifactID:version[:classifier]`. |
| `validate FILE...` | Check files like an upload does, without uploading them. The repository is optional and only needed to resolve references (VES, ZIP). |
| `exists COORDINATE EXTENSION` | Check if an artefact exists. |
| `ves add FILE` | Check a VES file and upload it. The coordinate is taken from the file. |
| `ves add-status FILE` | Check a VES status file and upload it. The coordinate is taken from the file. To change an existing status use `ves deprecate` or `ves validity`. |
| `ves deprecate VESID --author NAME [--reason TEXT] [--replacement VESID]` | Mark an existing VES as deprecated. The replacement must exist in the repository. |
| `ves validity VESID --author NAME [--from DATETIME] [--to DATETIME]` | Set the validity dates of an existing VES. |
| `ves latest GROUP:ARTIFACT` | Print the VESID of the latest version (including snapshots). |
| `ves latest-release GROUP:ARTIFACT` | Print the VESID of the latest release version (excluding snapshots). |
| `ves latest-active GROUP:ARTIFACT [--at DATETIME]` | Print the VESID of the latest version that is active according to its status at the given time (default: now). |

All commands support `-v` (verbose, including stack traces), `-q` (only warnings and errors) and `-h`.

The `ves latest*` commands print only the resolved VESID on the standard output, so that it can be used in scripts.
They use `VESLoaderPseudoVersionResolver` from phive, which needs the per-artefact ToC of the repository.
"Active" means "within the validity period of the status"; a deprecated VES is currently still considered active (see [phive#36](https://github.com/phax/phive/issues/36)).

Date and time values use the ISO 8601 format with offset, e.g. `2026-01-01T00:00:00Z`.

## Trust Measures

| Measure | How |
|---------|-----|
| Content check before writing | Every upload is checked by the content validator for its file extension. Files with an extension without a check are refused unless `--allow-unknown-extension` is used (`upload` only). |
| No silent overwrite | An existing artefact is only replaced with `--overwrite`. |
| Pinned input | `--expected-sha256 HEX` (all upload commands) refuses the upload if the SHA-256 hash value of the file differs. Without it, the hash value is printed for the record. |
| Integrity in the repository | ph-diver writes a `.sha256` file next to every artefact and verifies it on read. |
| Traceable status changes | `ves deprecate` and `ves validity` require `--author`, which is recorded in the status history. |
| Checked status changes | The modified status is checked like an uploaded `.status` file before it is written. |
| No accidental repository | `--basedir` must exist; configured repositories must be explicitly writable. |

A status whose hash value does not match its content is reported as a warning when it is modified.
`PhiveUploader.setFailOnStatusHashMismatch (true)` turns this into an error; the command line client does not enable it yet.

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success. Also for `ves deprecate` / `ves validity` when the status already had the requested values. |
| 1 | An expectation was not met: invalid content, hash value mismatch of the input, artefact or VES not found, no version matches. |
| 2 | Invalid command line (missing or contradicting options, invalid coordinate, missing file or directory). |
| 3 | Technical error: I/O, invalid repository configuration, failed write. |
| 4 | The artefact already exists and `--overwrite` was not used. |

## Docker

The image `phelger/scuba` contains the shaded JAR and uses `/data` as working directory:

```bash
docker run --rm -v "$PWD:/data" phelger/scuba upload --basedir repo com.example:schema:1.0.0 schema.xsd
```

The container runs as `root` by default, so files written to the mounted volume are owned by `root`; use `--user "$(id -u):$(id -g)"` to keep the ownership of the host user.

The image is built and pushed for `linux/amd64` and `linux/arm64` with `docker/build-all.sh` after `mvn clean install` of the released version.
