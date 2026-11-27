import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val revibeVersion = providers.gradleProperty("revibeVersion").get()

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.jna.platform)
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.revibe.desktop.MainKt"

        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Drevibe.version=$revibeVersion")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Revibe"
            packageVersion = revibeVersion
            description = "Revibe - Your music. Your vibe."
            vendor = "Revibe"
            copyright = "Copyright 2026 Revibe"
            modules("java.sql", "java.naming", "jdk.httpserver", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icons/revibe.ico"))
                menuGroup = "Revibe"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "96abee54-a741-4118-b1cd-23080db60d33"
            }
        }
    }
}
