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
    api(platform(libs.okhttp.bom))
    api(platform(libs.jackson.bom))

    api(libs.retrofit)
    api(libs.retrofit.converter.jackson)
    api(libs.okhttp)
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.jspecify) // nullness annotations are part of the public API

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.okhttp.mockwebserver)
}

nullaway {
    onlyNullMarked = true // packages opt in via @NullMarked in package-info.java
}

// Error Prone is used only as the vehicle for NullAway, which verifies the JSpecify annotations on
// main code. All other Error Prone checks are disabled to keep the build focused and quiet.
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks = true
        if (name == "compileJava") {
            nullaway {
                error()
                jspecifyMode = true
            }
        } else {
            nullaway { disable() }
        }
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
