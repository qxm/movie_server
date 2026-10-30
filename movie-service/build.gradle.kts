plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.example.movieapp"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
    implementation(project(":proto"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("io.grpc:grpc-netty-shaded:1.75.0")
    implementation("io.grpc:grpc-protobuf:1.75.0")
    implementation("io.grpc:grpc-stub:1.75.0")

    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Fast, isolated tests. Existing *ApplicationTests remain in the regular test task.
tasks.register<Test>("unitTest") {
    group = "verification"
    description = "Runs isolated unit tests without starting Spring services or databases."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*Test") }
    val temporaryDirectory = layout.buildDirectory.dir("tmp/unitTest").get().asFile
    systemProperty("java.io.tmpdir", temporaryDirectory.absolutePath)
    doFirst { temporaryDirectory.mkdirs() }
}

// Spring slices with an embedded database or the real GraphQL schema; no servers.
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs isolated persistence and GraphQL schema integration tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*IntegrationTests") }
    val temporaryDirectory = layout.buildDirectory.dir("tmp/integrationTest").get().asFile
    systemProperty("java.io.tmpdir", temporaryDirectory.absolutePath)
    doFirst { temporaryDirectory.mkdirs() }
}
