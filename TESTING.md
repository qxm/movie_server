# Backend unit tests

## Run

Use JDK 21 from the repository root:

```powershell
.\gradlew.bat :movie-service:unitTest :people-service:unitTest :graphql-gateway:unitTest
```

Linux/macOS:

```sh
./gradlew :movie-service:unitTest :people-service:unitTest :graphql-gateway:unitTest
```

These tasks select classes ending in `Test`. They use the existing JUnit 5 and Mockito dependencies. No application services, database, gRPC server, or HTTP server need to be running. Artwork tests create real small images and use per-test temporary directories under each module's build directory; existing posters are not touched.

The existing `*ApplicationTests` are Spring context smoke tests and remain part of the regular `test` task. They are intentionally outside `unitTest`; a passing unit suite does not confirm database configuration, transaction rollback, HTTP multipart limits, GraphQL schema binding, or network transport behavior.

If the Kotlin daemon cannot write to its default cache, run with `-Pkotlin.compiler.execution.strategy=in-process`. If the wrapper home is not writable in your environment, set `GRADLE_USER_HOME` to a writable directory before running Gradle.

## Coverage

- `MovieServiceTest`: trimmed fields, optional release year, year/title/character limits, normalized roles and characters, duplicate credits, multiple roles/characters, person existence checks before writes, dependency failures, get/list grouping, ordered deletes, missing records, update validation, and credit replacement.
- `PersonServiceTest`: trimmed names and biographies, null/empty biography, name limits, missing records, updates, deletes, sorted lists, blank searches, and the twenty-result search limit.
- `MovieGrpcServiceTest`: request/response conversion including credits, malformed UUIDs, validation errors, missing movies, preservation of gRPC failures, unexpected errors, and response completion semantics.
- `PeopleGrpcServiceTest`: nullable biography conversion, create/get/list/update/delete/search requests, invalid UUIDs, validation failures, missing people, and internal service errors.
- `ArtworkControllerTest`: valid PNG/JPEG content, generated filenames, header/extension mismatch, empty/oversized/exact-limit/truncated images, unsupported formats, missing files, invalid filenames, and response headers/bytes.
- `MovieGraphQLControllerTest`: delegation of queries/mutations, input forwarding, NOT_FOUND-to-null handling, service outages, and nullable credit-person resolution.

HTML reports are generated under `<module>/build/reports/tests/unitTest/index.html`. Machine-readable JUnit XML is under `<module>/build/test-results/unitTest/`.

AI assistance was used to author these tests and the unitTest Gradle tasks. The tests instantiate production classes directly and mock their dependencies; they do not establish full end-to-end coverage.
