import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
}

group = "com.conduit"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

dependencies {
    // Blockchain Relay Utility from Jitpack
    implementation("com.github.charliepank:blockchain-relay-utility:v0.3.3")
    
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    // Web3j dependencies
    implementation("org.web3j:core:4.9.8")
    implementation("org.web3j:crypto:4.9.8")
    implementation("org.web3j:contracts:4.9.8")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // OpenAPI/Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
    implementation("org.springdoc:springdoc-openapi-starter-common:2.2.0")
    
    // HTTP Client for user service communication
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    
    // Caffeine caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    
    // API Validation Dependencies
    implementation("io.swagger.parser.v3:swagger-parser:2.1.16")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Configure Spring Boot main class
springBoot {
    mainClass.set("com.conduit.chainservice.ChainServiceApplicationKt")
}

// Function to load environment variables from .env.local file
fun loadEnvFile() {
    val envFile = file(".env.local")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            val trimmedLine = line.trim()
            if (!trimmedLine.startsWith("#") && trimmedLine.contains("=")) {
                val (key, value) = trimmedLine.split("=", limit = 2)
                System.setProperty(key.trim(), value.trim())
            }
        }
    }
}

// Load .env.local before any tasks run
loadEnvFile()

// API Validation Task Properties
val apiValidationEnabled: String by project.extra { "true" }
val apiValidationFailOnMismatch: String by project.extra { "false" }
val apiValidationTimeout: String by project.extra { "30000" }
val apiValidationEnvironment: String by project.extra { "development" }

// API Validation Tasks
tasks.register<JavaExec>("validateApiDependencies") {
    group = "verification"
    description = "Validates API compatibility with dependent services"
    
    dependsOn("compileKotlin")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.conduit.chainservice.validation.ApiValidationRunner"
    
    // Pass properties as system properties
    systemProperty("api.validation.enabled", apiValidationEnabled)
    systemProperty("api.validation.failOnMismatch", apiValidationFailOnMismatch)
    systemProperty("api.validation.timeout", apiValidationTimeout)
    systemProperty("api.validation.environment", apiValidationEnvironment)
    
    // Pass environment variables for service URLs (loaded from .env.local or environment)
    systemProperty("USER_SERVICE_URL", System.getProperty("USER_SERVICE_URL") ?: System.getenv("USER_SERVICE_URL") ?: "http://localhost:8080")
    systemProperty("CONTRACT_SERVICE_URL", System.getProperty("CONTRACT_SERVICE_URL") ?: System.getenv("CONTRACT_SERVICE_URL") ?: "http://localhost:8080")
    systemProperty("EMAIL_SERVICE_URL", System.getProperty("EMAIL_SERVICE_URL") ?: System.getenv("EMAIL_SERVICE_URL") ?: "http://localhost:8979")
}

// Integrate API validation into check task
tasks.named("check") {
    dependsOn("validateApiDependencies")
}

tasks.register<JavaExec>("validateUserServiceApi") {
    group = "verification"
    description = "Validates API compatibility with User Service"
    
    dependsOn("compileKotlin")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.conduit.chainservice.validation.ApiValidationRunner"
    
    args = listOf("user-service")
    systemProperty("api.validation.enabled", apiValidationEnabled)
    systemProperty("api.validation.timeout", apiValidationTimeout)
    systemProperty("USER_SERVICE_URL", System.getProperty("USER_SERVICE_URL") ?: System.getenv("USER_SERVICE_URL") ?: "http://localhost:8080")
}

tasks.register<JavaExec>("validateContractServiceApi") {
    group = "verification"
    description = "Validates API compatibility with Contract Service"
    
    dependsOn("compileKotlin")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.conduit.chainservice.validation.ApiValidationRunner"
    
    args = listOf("contract-service")
    systemProperty("api.validation.enabled", apiValidationEnabled)
    systemProperty("api.validation.timeout", apiValidationTimeout)
    systemProperty("CONTRACT_SERVICE_URL", System.getProperty("CONTRACT_SERVICE_URL") ?: System.getenv("CONTRACT_SERVICE_URL") ?: "http://localhost:8080")
}

tasks.register<JavaExec>("validateEmailServiceApi") {
    group = "verification"
    description = "Validates API compatibility with Email Service"
    
    dependsOn("compileKotlin")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.conduit.chainservice.validation.ApiValidationRunner"
    
    args = listOf("email-service")
    systemProperty("api.validation.enabled", apiValidationEnabled)
    systemProperty("api.validation.timeout", apiValidationTimeout)
    systemProperty("EMAIL_SERVICE_URL", System.getProperty("EMAIL_SERVICE_URL") ?: System.getenv("EMAIL_SERVICE_URL") ?: "http://localhost:8979")
}


// Generate API validation report task
tasks.register<JavaExec>("generateApiValidationReport") {
    group = "documentation"
    description = "Generates detailed API validation report"
    
    dependsOn("compileKotlin")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.conduit.chainservice.validation.ApiValidationReportGenerator"
    
    systemProperty("api.validation.timeout", apiValidationTimeout)
    systemProperty("USER_SERVICE_URL", System.getProperty("USER_SERVICE_URL") ?: System.getenv("USER_SERVICE_URL") ?: "http://localhost:8080")
    systemProperty("CONTRACT_SERVICE_URL", System.getProperty("CONTRACT_SERVICE_URL") ?: System.getenv("CONTRACT_SERVICE_URL") ?: "http://localhost:8080")
    systemProperty("EMAIL_SERVICE_URL", System.getProperty("EMAIL_SERVICE_URL") ?: System.getenv("EMAIL_SERVICE_URL") ?: "http://localhost:8979")
}
