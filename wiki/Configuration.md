# Configuration

## Storage Backend Selection

ph-scuba delegates actual storage to ph-diver's `IRepoStorage` implementations.
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

`ScubaUploaderSettings` controls the upload behavior via chainable setters:

| Setting | Default | Description |
|---------|---------|-------------|
| `allowUploadWithUnknownExtension` | `false` | If `false`, uploads with a file extension that has no registered `IUploadContentValidatorSPI` are rejected. If `true`, they are allowed without content validation. |
| `allowOverwriteExisting` | `false` | If `false`, uploading to a key that already exists throws `RepoKeyAlreadyInUseException`. If `true`, the existing artifact is silently overwritten. |

Content validators are discovered automatically via Java `ServiceLoader` from all `IUploadContentValidatorSPI` implementations on the classpath. SHA-256 hashing, ToC management, and newline normalization are handled by the underlying ph-diver storage layer.

