import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

version = "0.1"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    application
    id("org.openjfx.javafxplugin") version "0.0.8"
}

repositories {
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.3.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.10.1")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.slf4j:log4j-over-slf4j:1.7.30")
    implementation("org.hsqldb:hsqldb:2.5.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.25.1")
}

javafx {
    version = "13.0.1"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClassName = "com.mpaulse.mobitra.Mobitra"
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
            jvmTarget = "11"
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

    register<Copy>("copyJpackager") {
        from("tools/jpackager/jpackager.exe")
        into("${Jvm.current().javaHome}/bin")
    }

    register<Copy>("copyLibs") {
        dependsOn("installDist")
        from("$buildDir/install/${project.name}/lib") {
            exclude("javafx-*.jar")
        }
        into("$buildDir/libs")
    }

    register<Copy>("copyModules") {
        dependsOn("installDist")
        from("$buildDir/install/${project.name}/lib") {
            include("javafx-*-win.jar")
        }
        into("$buildDir/modules")
    }

    register<Exec>("packageRelease") {
        dependsOn("copyJpackager", "copyLibs", "copyModules")
        commandLine(
            "${Jvm.current().javaHome}/bin/java",
            "--module-path",
            "tools/jpackager",
            "--add-opens",
            "jdk.jlink/jdk.tools.jlink.internal.packager=jdk.packager",
            "--module",
            "jdk.packager/jdk.packager.Main",
            "create-image",
            "--input",
            "$buildDir/libs",
            "--main-jar",
            "${project.name}-${project.version}.jar",
            "--module-path",
            "$buildDir/modules;tools/jpackager",
            "--add-modules",
            "javafx.controls,javafx.fxml",
            "--strip-native-commands",
            "--output",
            "release",
            "--identifier",
            "com.mpaulse.mobitra.Mobitra",
            "--name",
            "mobitra",
            "--version",
            project.version.toString(),
            "--description",
            "Mobitra - Telkom Mobile LTE Data Usage Tracker",
            "--icon",
            "src/main/resources/images/mobitra.ico",
            "--vendor",
            "Marlon Paulse",
            "--verbose")
    }

    register<Delete>("release") {
        dependsOn("packageRelease")
        delete = setOf("release/Mobitra/mobitra.ico")
    }

    clean {
        delete = setOf(
            buildDir,
            "release")
    }

}
