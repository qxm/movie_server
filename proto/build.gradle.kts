plugins {
    kotlin("jvm")
    `java-library`
    id("com.google.protobuf")
}

group = "org.example.movieapp"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api("io.grpc:grpc-protobuf:1.75.0")
    api("io.grpc:grpc-stub:1.75.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    testImplementation(kotlin("test"))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.8"
    }

    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.75.0"
        }
    }

    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}