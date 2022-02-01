import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileWriter
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

version = "1.1.0"

plugins {
    kotlin("jvm") version "1.6.10"
    application
    id("org.openjfx.javafxplugin") version "0.0.11"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.6.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.1")
    implementation("ch.qos.logback:logback-classic:1.2.10")
    implementation("org.slf4j:log4j-over-slf4j:1.7.35")
    implementation("org.hsqldb:hsqldb:2.6.1")
    implementation("net.java.dev.jna:jna-platform:5.10.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.32.0")
}

javafx {
    version = "17.0.2"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.mpaulse.mobitra.Mobitra")
}

configure<SourceSetContainer> {
    getByName("main") {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("src/generated/kotlin")
        }
    }
}

tasks {

    withType<KotlinCompile> {
        dependsOn("generateAppInfo", "copyLicenses")
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        manifest {
            attributes(
                "Main-Class" to "com.mpaulse.mobitra.Mobitra")
        }
        exclude("images\\*.xcf")
    }

    register<Copy>("generateAppInfo") {
        outputs.upToDateWhen {
            false // Force regeneration. Never skip task.
        }

        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val commitCount = ByteArrayOutputStream().use { output ->
            exec {
                commandLine("git", "rev-list", "HEAD", "--count")
                standardOutput = output
            }
            output.toString().trim()
        }

        from("src/main/kotlin/com/mpaulse/mobitra/AppInfo.kt.template")
        into("src/generated/kotlin/com/mpaulse/mobitra")
        rename(".kt.template", ".kt")
        expand(mutableMapOf(
            "version" to version,
            "build" to "$date.$commitCount"))
    }

    register<Copy>("copyLicenses") {
        from(".") {
            include("LICENSE*.txt")
        }
        into("src/main/resources")
    }

    register<Copy>("copyLibs") {
        dependsOn("installDist")
        from("$buildDir/install/${project.name}/lib") {
            exclude("javafx-*", "stax2*", "woodstox*")
            exclude("jackson-core*", "jackson-annotations*", "jackson-databind*")
        }
        into("$buildDir/libs")
    }

    register<Copy>("copyModules") {
        dependsOn("installDist")
        from("$buildDir/install/${project.name}/lib") {
            include("javafx-*-win*", "stax2*", "woodstox*")
            include("jackson-core*", "jackson-annotations*", "jackson-databind*")
        }
        into("$buildDir/modules")
    }

    register<Task>("buildRelease") {
        dependsOn("copyLibs", "copyModules")
        doLast {
            exec {
                delete("$buildDir/release")
                commandLine(
                    "${Jvm.current().javaHome}/bin/jpackage",
                    "--type",
                    "app-image",
                    "--input",
                    "$buildDir/libs",
                    "--main-jar",
                    "${project.name}-$version.jar",
                    "--module-path",
                    "$buildDir/modules",
                    "--add-modules",
                    "java.base,java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.scripting,java.sql,java.transaction.xa,java.xml,"
                        + "com.fasterxml.jackson.annotation,com.fasterxml.jackson.core,com.fasterxml.jackson.databind,"
                        + "javafx.base,javafx.controls,javafx.fxml,javafx.graphics,"
                        + "org.codehaus.stax2,com.ctc.wstx",
                    "--dest",
                    "$buildDir/release",
                    "--name",
                    "Mobitra",
                    "--app-version",
                    version.toString(),
                    "--description",
                    "Mobitra - Telkom Mobile Prepaid LTE Data Usage Tracker",
                    "--icon",
                    "src/main/resources/images/mobitra.ico",
                    "--vendor",
                    "Marlon Paulse",
                    "--copyright",
                    "Copyright (c) ${LocalDate.now().year} Marlon Paulse",
                    "--verbose")
            }
            delete {
                delete("build/release/Mobitra/Mobitra.ico", "build/release/Mobitra/.jpackage.xml")
            }
            copy {
                from(".") {
                    include("LICENSE*.txt")
                }
                into("build/release/Mobitra")
            }
        }
    }

    register<Zip>("zipRelease") {
        val archive = "Mobitra-${project.version}.zip"
        archiveFileName.set(archive)
        destinationDirectory.set(File("build/release"))
        from("build/release") {
            include("Mobitra/**")
        }
        doLast {
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(FileInputStream("build/release/$archive")).use { file ->
                val buf = ByteArray(8192)
                var n = file.read(buf, 0, 8192)
                while (n > 0) {
                    digest.update(buf, 0, n)
                    n = file.read(buf, 0, 8192)
                }
            }
            val checksum = digest.digest().joinToString(separator = "") { b ->
                "%02x".format(b)
            }
            FileWriter("build/release/$archive.sha256").use { file ->
                file.write("$checksum\n")
            }
        }
    }

    register<Task>("release") {
        dependsOn("buildRelease", "zipRelease")
    }

    clean {
        delete = setOf(buildDir)
    }

}
