import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("me.champeau.jmh") version "0.7.3"
}

group = "com.jacksonrakena"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["snippetsDir"] = file("build/generated-snippets")

dependencies {
    implementation(Spring.boot.actuator)
    implementation(Spring.boot.cache)
    implementation(Spring.boot.web)
    implementation("org.jetbrains.exposed:exposed-spring-boot4-starter:_")
    implementation(JetBrains.exposed.core)
    implementation("org.springframework.boot:spring-boot-starter-kotlinx-serialization-json:_")
    implementation(JetBrains.exposed.jdbc)
    implementation(KotlinX.serialization.json)
    implementation("com.h2database:h2:_")
    implementation("org.postgresql:postgresql:_")
    implementation("org.jobrunr:jobrunr-spring-boot-4-starter:_")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:_")
    implementation("io.github.oshai:kotlin-logging-jvm:_")

    implementation("org.jetbrains.kotlin:kotlin-reflect:_")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:_")
    implementation(Spring.boot.security)
    implementation("org.springframework.session:spring-session-jdbc:_")
    implementation("org.springframework.boot:spring-boot-liquibase:_")
    implementation("org.liquibase:liquibase-core:_")
    developmentOnly(Spring.boot.devTools)
    annotationProcessor(Spring.boot.configurationProcessor)
    implementation("org.jobrunr:jobrunr-kotlin-2.2-support:_")
    testImplementation(Spring.boot.test)
    testImplementation(Kotlin.test.junit5)
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc:_")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:_")
    testImplementation(KotlinX.coroutines.test)
    testImplementation(Testing.kotest.assertions.core)
}

dependencies {
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    jmh(KotlinX.coroutines.core)
    jmh("org.jetbrains.kotlinx:kotlinx-datetime:_")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

jmh {
    warmupIterations = 2
    iterations = 5
    fork = 1
    jvmArgs = listOf("-Xms512m", "-Xmx2g")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    outputs.dir(project.extra["snippetsDir"]!!)
}

tasks.named<BootBuildImage>("bootBuildImage") {
	imageName.set("ghcr.io/jacksonrakena/mixer:latest")
}
