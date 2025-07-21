import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    id("io.ktor.plugin") version "2.3.10"
    kotlin("plugin.serialization") version "1.9.20"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.chargepoint"
version = "0.0.1"
application {
    mainClass.set("com.chargepoint.asynccharging.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    // Ktor server
    implementation("io.ktor:ktor-server-core:2.3.10")
    implementation("io.ktor:ktor-server-netty:2.3.10")

    // Content negotiation & JSON
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.10")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.10")

    // Ktor server plugins
    implementation("io.ktor:ktor-server-call-logging:2.3.10")
    implementation("io.ktor:ktor-server-cors:2.3.10")
    implementation("io.ktor:ktor-server-default-headers:2.3.10")
    implementation("io.ktor:ktor-server-status-pages:2.3.10")

    // HTTP client
    implementation("io.ktor:ktor-client-core:2.3.10")
    implementation("io.ktor:ktor-client-cio:2.3.10")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.10")
    implementation("io.ktor:ktor-client-serialization:2.3.10")

    // Database
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.7.7")

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.13")

    // Jedis for Redis Queue integration
    implementation("redis.clients:jedis:5.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests:2.3.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.ktor:ktor-client-mock:2.3.10")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    useJUnitPlatform()
}