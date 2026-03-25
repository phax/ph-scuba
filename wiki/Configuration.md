# Configuration

## Storage Backend Selection

SCUBA delegates actual storage to ph-diver's `IRepoStorage` implementations.
The consumer configures which backend(s) to use when creating the uploader.

### Local Filesystem

The simplest option, writing artifacts to a directory on disk.

```java
IRepoStorageWithToc storage = new RepoStorageLocalFileSystem (
    new File ("/path/to/repository"),
    ERepoWritable.WITH_WRITE,
    ERepoDeletable.WITH_DELETE
);
```

### HTTP Server

Uploads artifacts to a remote diver repository via HTTP PUT.

```java
IRepoStorageWithToc storage = new RepoStorageHttp (
    "https://repo.example.com/repository"
);
```

### AWS S3

Uploads artifacts to an S3 bucket.

```java
IRepoStorageWithToc storage = new RepoStorageS3 (
    s3Client,
    "my-bucket-name",
    "optional/key/prefix/"
);
```

### Storage Chain

Multiple backends can be combined.
Reads fall through in order; writes go to all writable storages.

## Uploader Configuration

The uploader supports configuration options such as:

| Option | Default | Description |
|--------|---------|-------------|
| Overwrite existing | `false` | Whether to allow overwriting existing artifacts |
| Verify hash on read | `true` | Verify SHA-256 on read-back |
| Normalize newlines | `true` | Convert XML content to UNIX newlines before upload |
| Validators | (built-in) | `IUploadContentValidatorSPI` implementations, discovered via SPI and dispatched by file extension |

