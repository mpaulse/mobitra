import org.gradle.internal.jvm.Jvm
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.3.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.10.1")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.slf4j:log4j-over-slf4j:1.7.29")
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

tasks {

    withType<KotlinCompile> {
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
        commandLine = listOf(
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
            "Mobitra",
            "--version",
            project.version.toString(),
            "--description",
            "Mobitra - Telkom Mobile LTE Data Usage Tracker",
            // TODO: icon
            //"--icon",
            //"src/main/resources/images/Mobitra.ico",
            "--vendor",
            "Marlon Paulse",
            "--verbose")
    }

    register<Delete>("release") {
        dependsOn("packageRelease")
        delete = setOf("release/Mobitra/Mobitra.ico")
    }

    clean {
        delete = setOf(
            buildDir,
            "release")
    }

}
