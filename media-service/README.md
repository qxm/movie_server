# Media service

Standalone Spring Boot image upload and retrieval service. It has no database, movie-service dependency, or gRPC server. Default HTTP port: **8082**.

From the backend root:

```powershell
.\gradlew.bat :media-service:bootRun
```

## API

- `POST /api/artworks`: multipart `file`; returns `{ "url": "/api/artworks/<uuid>.<extension>" }`.
- `GET /api/artworks/{filename}`: image bytes with the detected PNG, JPEG, or WebP content type and public one-day cache headers.

Maximum file size is 5 MB, maximum multipart request size is 6 MB. The controller checks actual decoded image bytes. Existing filename validation and all 21 controller unit tests were migrated unchanged.

The public `/api/artworks` paths remain stable for existing movie `artworkUrl` values. The frontend forwards these paths to `MEDIA_SERVICE_URL`, defaulting to `http://127.0.0.1:8082`. The old `MOVIE_SERVICE_URL` no longer controls image requests. Direct HTTP calls to movie-service on port 8081 must switch to media-service; movie-service is now gRPC-only on 9091.

## Storage and existing images

Default storage is `./uploads/artworks`, relative to the service working directory. For `:media-service:bootRun` this is the media-service module. Persist that directory between restarts. Override `MEDIA_UPLOAD_DIR` or Spring's `app.artwork.upload-dir` to use an explicit absolute directory.

Before switching an existing installation, stop uploads to the old movie-service and copy its files. From the backend root, for the standard old module directory:

```powershell
.\media-service\scripts\migrate-artworks.ps1 -SourceDirectory .\movie-service\uploads\artworks
```

If the old application ran from the repository root, use `.\uploads\artworks` instead. For a custom old upload directory, pass that actual path. The script copies only generated artwork filenames, verifies hashes, skips identical existing files, rejects conflicting files, and never deletes originals. Re-run after stopping old uploads if files were added during migration. Keep originals until the new service has been verified.

Alternatively, set `MEDIA_UPLOAD_DIR` to the existing absolute upload directory before starting media-service. Do not point tests at real user storage.

## Package and test

```powershell
.\gradlew.bat :media-service:bootJar
.\gradlew.bat :media-service:unitTest :media-service:integrationTest
```

Run the executable JAR from the module directory to preserve default storage resolution:

```powershell
cd media-service
java -jar .\build\libs\media-service-0.0.1-SNAPSHOT.jar
```

HTTP integration tests start media-service independently on a random port with a temporary storage directory. They verify PNG/WebP round trips, missing files, invalid content, and multipart size enforcement. No other backend service is required.

The same upload endpoint serves movie posters and person photos. Movies store `artworkUrl`; people store `photoUrl`. This service does not add upload confirmation, temporary storage, or automatic orphan-image cleanup. Removing a business reference does not immediately delete the image file.
