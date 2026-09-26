import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

abstract class VerifySourcesPublicationTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val sourcesJarFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.Input
    abstract val publicationIncludesSources: org.gradle.api.provider.Property<Boolean>

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val sourcesJar = sourcesJarFile.get().asFile
        require(sourcesJar.isFile) {
            "Expected sources jar to be built at ${sourcesJar.absolutePath}"
        }
        require(publicationIncludesSources.get()) {
            "Maven publication 'mavenJava' must include the sources jar."
        }
    }
}

plugins {
    `java-library`
    `maven-publish`
    jacoco
    alias(libs.plugins.errorprone)
    alias(libs.plugins.nullaway)
    alias(libs.plugins.spotless)
}

group = "io.github.dimazigel"
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

tasks.jar {
    // Stable JPMS name for module-path consumers; without it the name derives from the jar filename.
    manifest.attributes("Automatic-Module-Name" to "io.github.dimazigel.yfinance")
}

// Formatting is deliberately light: the codebase's existing style (4-space indent, ~110 columns) is
// kept as-is; Spotless only pins import order (static first, then one lexicographic block), unused
// imports, trailing whitespace and final newlines. `./gradlew spotlessApply` fixes, `check` verifies.
spotless {
    java {
        target("src/*/java/**/*.java")
        importOrder("\\#", "")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// GitHub Packages is the only publishing destination (Maven Central was considered and declined).
// Workflows publish with the explicit publishAllPublicationsToGitHubPackagesRepository task.
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/dimazigel/yfinance-java")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "yfinance-java"
                description = "Java 21 client for Yahoo Finance market data (port of Python yfinance)"
                url = "https://github.com/dimazigel/yfinance-java"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "dimazigel"
                        name = "Dmitry Tsigelnik"
                        url = "https://github.com/dimazigel"
                    }
                }
                scm {
                    url = "https://github.com/dimazigel/yfinance-java"
                    connection = "scm:git:https://github.com/dimazigel/yfinance-java.git"
                }
            }
        }
    }
}

val builtSourcesJarFile = tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile }
val mavenJavaPublishesSources = publishing.publications
    .named<MavenPublication>("mavenJava")
    .get()
    .artifacts
    .any { it.classifier == "sources" }

tasks.register<VerifySourcesPublicationTask>("verifySourcesPublication") {
    description = "Verifies the Maven publication includes the sources jar."
    group = "verification"
    dependsOn(tasks.named("sourcesJar"))
    sourcesJarFile.set(builtSourcesJarFile)
    publicationIncludesSources.set(mavenJavaPublishesSources)
}

// Dedicated source set for live integration tests that hit real Yahoo Finance.
val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations[integrationTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    // `api`: types that appear in the public API (HttpUrl/OkHttpClient.Builder in EndpointConfig,
    // Retrofit in YahooApis, JSpecify annotations everywhere). Jackson is an implementation detail:
    // consumers see it at runtime only.
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.jspecify)
    api(libs.slf4j.api) // consumers bind their own backend; only the API is a dependency

    implementation(platform(libs.jackson.bom))
    implementation(libs.retrofit.converter.jackson)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    implementation(platform(libs.feign.bom))
    implementation(libs.feign.core)
    implementation(libs.feign.okhttp)
    implementation(libs.feign.jackson3)
    implementation(platform(libs.jackson3.bom))
    implementation(libs.jackson3.databind)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.logback.classic) // real MDC (slf4j-simple has a no-op one) and ListAppender for log assertions
}

nullaway {
    onlyNullMarked = true // packages opt in via @NullMarked in package-info.java
}

// Main code compiles with Error Prone's default checks plus NullAway (JSpecify mode) and -Werror, so
// any finding fails the build instead of scrolling past. Test code is compiled without Error Prone.
tasks.withType<JavaCompile>().configureEach {
    val isMain = name == "compileJava"
    options.errorprone {
        disableAllChecks = !isMain
        if (isMain) {
            nullaway {
                error()
                jspecifyMode = true
            }
        } else {
            nullaway { disable() }
        }
    }
    if (isMain) {
        options.compilerArgs.add("-Werror")
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("live")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// Coverage floor (measured 88% line / 64% branch when introduced); `check` fails below it.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs live integration tests against real Yahoo Finance endpoints."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform {
        includeTags("live")
    }
    shouldRunAfter(tasks.test)
}
