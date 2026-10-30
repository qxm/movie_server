plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.example.movieapp"
version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.register<Test>("unitTest") {
    group = "verification"
    description = "Runs isolated media controller tests."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*Test") }
    val temporaryDirectory = layout.buildDirectory.dir("tmp/unitTest").get().asFile
    systemProperty("java.io.tmpdir", temporaryDirectory.absolutePath)
    doFirst { temporaryDirectory.mkdirs() }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs media-service HTTP integration tests on a random port."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*IntegrationTests") }
    val temporaryDirectory = layout.buildDirectory.dir("tmp/integrationTest").get().asFile
    systemProperty("java.io.tmpdir", temporaryDirectory.absolutePath)
    doFirst { temporaryDirectory.mkdirs() }
}
