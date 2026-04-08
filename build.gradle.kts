plugins {
    kotlin("jvm") version "2.0.20"
    `maven-publish`
    `java-test-fixtures`
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "org.sbm4j"
version = "1.1.13"

repositories {
    mavenCentral()
}


val kotlinVersion: String by project
val logbackVersion: String by project
val coroutinesVersion: String by project
val mockkVersion: String by project
val hamkrestVersion: String by project
val kotlinLoggingVersion: String by project


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")

    implementation(kotlin("reflect"))

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation(kotlin("test"))
    testImplementation("com.natpryce:hamkrest:$hamkrestVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${coroutinesVersion}")

    testFixturesImplementation("io.mockk:mockk:${mockkVersion}")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${coroutinesVersion}")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${coroutinesVersion}")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${coroutinesVersion}")
    testFixturesImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testFixturesImplementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    //testFixturesImplementation(kotlin("test"))
    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testFixturesImplementation("com.natpryce:hamkrest:${hamkrestVersion}")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("testFixturesSourcesJar") {
    archiveClassifier.set("test-fixtures-sources")
    from(sourceSets["testFixtures"].allSource)
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    withJavadocJar()
}



publishing{
    repositories {
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/sandrineBeauche/meercat")
            credentials{
                username = System.getenv("GITHUB_PACKAGE_REGISTRY_USER")
                password = System.getenv("GITHUB_PACKAGE_REGISTRY_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.sbm4j"
            artifactId = "meercat"
            version = "$version"
            from(components["java"])
            artifact(tasks["testFixturesSourcesJar"])
        }
    }
}