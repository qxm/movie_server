# Movie Manager — Deployment and User Guide

## 1. Overview

Movie Manager manages a movie collection and its cast and crew. The frontend uses SvelteKit and TypeScript. The backend consists of Kotlin/Spring Boot services communicating over gRPC, with a GraphQL gateway for frontend requests.

Source repositories:

- Frontend: [qxm/movie_client](https://github.com/qxm/movie_client)
- Backend: [qxm/movie_server](https://github.com/qxm/movie_server)

| Component | Default address | Purpose |
| --- | --- | --- |
| Frontend, development | `http://localhost:5173` | Browser UI and same-origin API proxies |
| GraphQL gateway | `http://localhost:8080/graphql` | GraphQL queries and mutations |
| GraphiQL | `http://localhost:8080/graphiql` | Interactive API explorer |
| Movie service | gRPC `9091` | Movies and credits |
| Media service | HTTP `8082` | Image upload, validation, and retrieval |
| People service | gRPC `9092` | People and biographies |

Browser requests to `/api/graphql` are forwarded by the frontend server to the gateway. Poster upload and retrieval requests under `/api/artworks` are forwarded to media-service. The movie and people services have no HTTP interface.

## 2. Prerequisites

- JDK 21, with `JAVA_HOME` and `PATH` configured.
- Git, available on `PATH`.
- Node.js 24 and npm. Node.js `24.13.0` was used during development.
- Internet access for the first npm installation and Gradle dependency download.
- A modern browser.
- Free ports `5173`, `8080`, `8082`, `9091`, and `9092`.

No separate database server, global Gradle installation, or global Protobuf compiler is required. The repository includes the Gradle wrapper and Protobuf build configuration. The services use file-based H2 databases.

Verify the tools in PowerShell:

```powershell
java -version
node --version
npm.cmd --version
git --version
```

The following examples use this checkout layout. A different parent directory can be used by adjusting the `cd` commands:

```text
Backend:  C:\movie-assessment\movie_server
Frontend: C:\movie-assessment\movie_client
```

## 3. Quick Start on Windows

### 3.0 Clone the repositories

For a fresh checkout, run:

```powershell
New-Item -ItemType Directory -Force C:\movie-assessment | Out-Null
cd C:\movie-assessment
git clone https://github.com/qxm/movie_server.git
git clone https://github.com/qxm/movie_client.git
```

If the repositories have already been cloned, use the existing checkouts instead. The backend Gradle root is the `movie_server` directory; the frontend npm root is `movie_client`.

### 3.1 Prepare the backend

Open PowerShell in the backend root:

```powershell
cd C:\movie-assessment\movie_server
.\gradlew.bat :proto:generateProto
```

Gradle also generates the protocol classes when required by compilation. The explicit command above is a useful first setup check.

### 3.2 Start the backend services

Use four separate terminals. Run each command from the backend root and leave the terminal open. Wait for each service to finish starting before proceeding to the next one.

**Terminal 1 — People service**

```powershell
cd C:\movie-assessment\movie_server
.\gradlew.bat :people-service:bootRun
```

**Terminal 2 — Movie service**

```powershell
cd C:\movie-assessment\movie_server
.\gradlew.bat :movie-service:bootRun
```

**Terminal 3 — GraphQL gateway**

```powershell
cd C:\movie-assessment\movie_server
.\gradlew.bat :graphql-gateway:bootRun
```

**Terminal 4 — Media service**

```powershell
cd C:\movie-assessment\movie_server
.\gradlew.bat :media-service:bootRun
```

The four backend services must be running for the complete application workflow. Media-service can upload and serve images independently of the other services. Movie creation checks person existence; person deletion checks movie associations.

### 3.3 Prepare and start the frontend

Open a fifth PowerShell terminal:

```powershell
cd C:\movie-assessment\movie_client
npm.cmd ci --include=optional
```

Create `.env` if it does not already exist:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

The supplied environment settings are:

```dotenv
GRAPHQL_GATEWAY_URL=http://localhost:8080
MEDIA_SERVICE_URL=http://127.0.0.1:8082
BODY_SIZE_LIMIT=6M
```

The URLs identify the backend services, without an appended `/graphql` or `/api/artworks` path. The frontend proxy adds the appropriate endpoint path.

Start the development server:

```powershell
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Open **http://localhost:5173**. Using `--strictPort` makes a port conflict explicit instead of silently switching to another port.

### 3.4 Verify connectivity

Open **http://localhost:8080/graphiql** and execute:

```graphql
query {
  movies {
    id
    title
    releaseYear
  }
  people {
    id
    name
  }
}
```

An empty movie list is valid on a fresh database. The people service inserts or updates the example person Christopher Nolan with a fixed ID when it starts.

## 4. Build and Run Packaged Applications

This section describes running the application without the development servers. It is a local deployment procedure; it does not provision public hosting, TLS, or authentication.

### 4.1 Package the backend

From the backend root:

```powershell
.\gradlew.bat :people-service:bootJar :movie-service:bootJar :graphql-gateway:bootJar :media-service:bootJar
```

Run the executable Spring Boot JARs in four separate terminals. Use the module directories as working directories so database and upload paths remain predictable:

```powershell
# Terminal 1
cd C:\movie-assessment\movie_server\people-service
java -jar .\build\libs\people-service-0.0.1-SNAPSHOT.jar
```

```powershell
# Terminal 2
cd C:\movie-assessment\movie_server\movie-service
java -jar .\build\libs\movie-service-0.0.1-SNAPSHOT.jar
```

```powershell
# Terminal 3
cd C:\movie-assessment\movie_server\graphql-gateway
java -jar .\build\libs\graphql-gateway-0.0.1-SNAPSHOT.jar
```

```powershell
# Terminal 4
cd C:\movie-assessment\movie_server\media-service
java -jar .\build\libs\media-service-0.0.1-SNAPSHOT.jar
```


Use the executable JARs, not files ending in `-plain.jar`. If the project version changes, use the corresponding generated filenames.

### 4.2 Build and start the frontend Node server

Ensure `.env` contains the intended backend URLs **before building**:

```powershell
cd C:\movie-assessment\movie_client
npm.cmd ci --include=optional
npm.cmd run build
$env:HOST = '127.0.0.1'
$env:PORT = '3000'
$env:ORIGIN = 'http://localhost:3000'
$env:BODY_SIZE_LIMIT = '6M'
node --env-file=.env build
```

Open **http://localhost:3000**. Keep the installed dependencies available when running this build. Rebuild after changing the frontend's backend URL settings, because the current frontend environment import is processed during the build. Set `ORIGIN` to the actual frontend origin if using a different address.

`BODY_SIZE_LIMIT=6M` allows a 5 MB poster plus multipart request overhead. The backend still enforces its own upload limit.

Do not run a packaged backend service alongside its `bootRun` instance using the same ports or H2 database files.

## 5. Configuration and Persistent Data

Backend configuration is stored in each service's `src/main/resources/application.yml`.

| Setting | Service | Default |
| --- | --- | --- |
| `server.port` | Gateway | `8080` |
| `server.port` | Media service | `8082` |
| `grpc.server.port` | Movie / People | `9091` / `9092` |
| `grpc.people.host`, `grpc.people.port` | Movie service and gateway | `localhost`, `9092` |
| `grpc.movie.host`, `grpc.movie.port` | Gateway | `localhost`, `9091` |
| `grpc.movie.host`, `grpc.movie.port` | People service | `127.0.0.1`, `9091` (code defaults) |
| `app.artwork.upload-dir` / `MEDIA_UPLOAD_DIR` | Media service | `./uploads/artworks` |
| `spring.datasource.url` | Movie service | `jdbc:h2:file:./data/moviedb` |
| `spring.datasource.url` | People service | `jdbc:h2:file:./data/peopledb` |

Relative paths are resolved against the service process's working directory. Preserve both H2 database files and the artwork directory between runs. Stop the services before copying database files for backup. Keep a consistent working directory to avoid accidentally starting with a different database.

For a different host or port, update every caller of that service as well. Spring Boot configuration can also be overridden with command-line arguments, for example:

```powershell
java -jar .\build\libs\people-service-0.0.1-SNAPSHOT.jar --grpc.movie.host=127.0.0.1 --grpc.movie.port=9091
```

WebP decoding in media-service uses the bundled TwelveMonkeys ImageIO dependency. No separate native WebP installation is required.

## 6. User Guide

### Browse the collection

The home page displays the movie collection. Use its filter to match titles, release years, or credited people. Change the sort order or switch between list and grid views. Director, cast, writer, and producer credits are displayed on separate rows. Select a movie title, poster, or **View movie** link to open its details.

### Add a movie

1. Select **Add Movie** in the navigation.
2. Enter a title. Release year, synopsis, poster, and credits are optional. A supplied release year must be between 1888 and 2100.
3. Upload a JPEG, PNG, or WebP poster of up to 5 MB, or supply a poster URL. The backend checks actual uploaded image content rather than trusting its filename.
4. Search for an existing person and select the result, or choose **Add New Person**, enter the person's details, and create them. A newly created person is automatically shown as **Selected**.
5. Choose a role: Actor, Director, Writer, or Producer. For actors, optionally enter a character name.
6. Select **Add to Movie** to add the credit. Repeat as needed. Creating or selecting a person alone does not add a movie credit.
7. Submit the movie. After a successful save, the movie details page opens.

A person can have multiple roles or actor characters. An identical person/role/character credit cannot be added twice. Creating a person saves that person independently, even if the movie form is subsequently abandoned.

### View, edit, and delete records

- Open a movie's details to view its information and credits, edit the movie, or delete it. Deleting a movie removes its credit records and leaves the person records available.
- Follow a credited person's name to open their details. The return link preserves the relevant navigation context. The person page supports editing and deletion.
- A person credited in any movie cannot be deleted through the normal deletion workflow. Remove their credits from the relevant movies and save those movies first. The delete dialog displays the backend's explanation.
- If the movie service is unavailable or the association check times out, person deletion is refused. Restore the service and retry.

### Search

Open **Search**, enter a movie title or person name, and submit the query. Use the result categories to view all results, movies, or people. Select a result to view its details. Person searches return at most 20 matches.

## 7. Automated Tests

### Backend unit tests

Run from the backend root:

```powershell
.\gradlew.bat :movie-service:unitTest :people-service:unitTest :graphql-gateway:unitTest :media-service:unitTest
```

These tests cover business validation, gRPC request/response and error handling, image upload validation, client deadlines, and controller behavior using mocked dependencies.

### Backend isolated integration tests

```powershell
.\gradlew.bat :movie-service:integrationTest :people-service:integrationTest :graphql-gateway:integrationTest :media-service:integrationTest
```

These tests use isolated in-memory H2 databases and the actual GraphQL schema with mocked downstream clients. They cover persistence, credit replacement, transaction rollback, search behavior, schema binding, and person deletion protection. Media-service integration tests start a standalone HTTP server on a random port and verify real multipart uploads. No pre-existing backend services are required, and the existing application databases and image directories are not used.

Reports are generated at:

```text
<module>/build/reports/tests/unitTest/index.html
<module>/build/reports/tests/integrationTest/index.html
```

The ordinary Gradle `test` task also includes the existing application context smoke tests. Those tests are separate from the isolated tasks above and may initialize the application's configured database and service ports.

### Frontend checks and tests

Run from the frontend root:

```powershell
npm.cmd run check
npm.cmd run test:unit -- --run --project server
npm.cmd run build
```

The `server` project name refers to tests running in Node, including frontend API helpers; it does not mean the Spring Boot backend. Network calls are mocked.

To include browser component tests, first install the Playwright browser required by the installed version, then run the complete suite:

```powershell
npx.cmd playwright install chromium
npm.cmd test
```

Component tests require that browser installation; the Node-based unit suite does not.

### Suggested manual acceptance check

Create a person from **Add Movie** and confirm automatic selection. Add them as an actor and director, upload a WebP poster, and save the movie. Verify the separate credit rows and poster after refreshing. Open the person and confirm deletion is blocked. Remove both credits from the movie, save, and confirm that person deletion now succeeds. Use temporary records for this check.

## 8. Troubleshooting

| Symptom | Action |
| --- | --- |
| `/api/graphql` returns `502` or `ECONNREFUSED` | Confirm the gateway is running on `8080` and check `GRAPHQL_GATEWAY_URL`. Also verify both gRPC services are running. Restart the frontend after editing its environment file. |
| Poster upload returns `502` | Confirm media-service is running on `8082` and check `MEDIA_SERVICE_URL`. |
| Upload returns `413` | Confirm the file is at most 5 MB and set the production frontend's `BODY_SIZE_LIMIT=6M`. |
| WebP upload is rejected by an older running instance | Restart media-service with the current source/build so the ImageIO plugin is included. Refresh the frontend as well. |
| Person deletion is blocked | Remove all of that person's movie credits first. If the message indicates an unavailable backend, restore the movie service. |
| Address already in use | Stop the previous instance, or change the port and all dependent service URLs. Avoid duplicate IDE and terminal instances. |
| H2 database is locked | Stop the other process using the same database file. Do not delete the database to resolve a process conflict. |
| Kotlin daemon cache permission error | Append `'-Pkotlin.compiler.execution.strategy=in-process'` to the Gradle command. Ensure the Gradle cache directory is writable. |
| Playwright executable is missing | Run `npx.cmd playwright install chromium`, or run only the `server` unit-test project. |

If npm reports a missing optional native binding, stop the frontend and retry:

```powershell
npm.cmd ci --include=optional
```

This reinstalls dependencies from the submitted lockfile. If the lockfile itself needs regeneration, preserve a copy before changing it and review the resulting diff.

When calling an executable by a quoted absolute path in PowerShell, use the call operator:

```powershell
& 'C:\Program Files\nodejs\npm.cmd' install --include=optional
```

For an unexpected frontend `500`, inspect the frontend terminal output. For backend failures, inspect the corresponding service terminal. Record the failing request and error message before retrying.

## 9. Shutdown and Current Limitations

Use **Ctrl+C** in each running terminal. Stop the frontend and gateway first, followed by the media, movie and people services. Keep database files and artwork files for the next run.

- This assessment deployment has no authentication or authorization and uses plaintext local gRPC connections. It is intended for a local demonstration rather than unrestricted public exposure.
- The person deletion guard scans the existing movie list. It blocks normal deletion of referenced people and refuses deletion when verification fails, but the check and delete are not an atomic cross-service transaction. Concurrent credit creation and person deletion can still race.
- Uploaded posters may remain unused if movie creation is abandoned or a poster is replaced. Automatic orphan-file cleanup is not implemented.
- The startup seed can restore or overwrite the example Christopher Nolan record on restart.
- The isolated test suites do not establish full cross-process HTTP-to-GraphQL-to-gRPC end-to-end coverage. Browser component tests depend on a separately installed Playwright browser.

## 10. Submission Contents

Include both source repositories, their dependency manifests and lockfiles, the Gradle wrapper, `.env.example`, tests, and this guide. Generated `node_modules`, build outputs, local caches, and IDE-specific files are not needed for a source submission. Include sample databases or uploaded images only if required for the assessment, and document their working-directory layout.

AI assistance was used during development for selected implementation changes, tests, and documentation. The submitted source and its behavior remain the basis for evaluation.


## Media service migration

Images are now owned by media-service on HTTP 8082. Movie records still store `artworkUrl`, and same-origin `/api/artworks/<filename>` URLs are unchanged. The frontend uses `MEDIA_SERVICE_URL`; its former `MOVIE_SERVICE_URL` setting no longer routes image requests. Restart the frontend after editing its environment, and rebuild a production frontend after environment changes. Direct clients of the former movie-service HTTP endpoint on 8081 must switch to 8082.

Before switching existing data, stop uploads to the old movie-service. From the backend root, copy images with:

```powershell
.\media-service\scripts\migrate-artworks.ps1 -SourceDirectory .\movie-service\uploads\artworks
```

If the old service used another working directory, pass its actual upload path (for example `.\uploads\artworks`). The script preserves originals and verifies copied file hashes. For a custom storage location, set `MEDIA_UPLOAD_DIR` to an absolute path. Keep the media-service upload directory persistent. The backend repository's `media-service/README.md` contains detailed setup instructions.

This migration does not add person avatars, temporary uploads, or unused-image cleanup.
