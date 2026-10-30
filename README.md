# MovieDemo


# movie_server
## How to start
1. ./gradlew :proto:generateProto
2. ./gradlew :people-service:bootRun
3. ./gradlew :movie-service:bootRun
4. ./gradlew :graphql-gateway:bootRun
5. ./gradlew :media-service:bootRun


Images are handled by media-service (HTTP 8082); movie-service is now gRPC-only (9091). See [media-service setup and image migration](media-service/README.md) and [deployment guide](DEPLOYMENT_AND_USER_GUIDE.md).
