plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.stevdrey"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }

// Reuse the independently reviewed baseline provenance; never package/download model weights.
tasks.processResources {
    from("../../infra/whisper/artifacts.json") {
        into("speech")
        rename { "whisper-artifacts.json" }
    }
}

// Keep public examples in one place for API tests and future clients.
tasks.processTestResources {
    from("../../contracts/exercises") {
        include("**/*.json")
        into("exercises")
    }
    from("../../contracts/actions") {
        include("**/*.json")
        into("actions")
    }
    from("../../contracts/assessments") {
        include("**/*.json")
        into("assessments")
    }
}
