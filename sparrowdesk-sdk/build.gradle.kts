plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

val sdkVersion: String = project.findProperty("SDK_VERSION") as? String ?: "0.1.1"

kotlin {
    // Suppress expect/actual classes beta warning
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.get().compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        publishLibraryVariants("release")
    }

    @Suppress("DEPRECATION")
    val xcf = org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig(project, "SparrowDeskSDK")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SparrowDeskSDK"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // No external dependencies
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            // Uses Android platform WebView APIs
            // Foreground/background hooks when attach() receives a LifecycleOwner
            implementation(libs.androidx.lifecycle.common)
        }

        iosMain.dependencies {
            // Uses iOS platform WebKit APIs via Kotlin/Native
        }
    }
}

android {
    namespace = "com.sparrowdesk.sdk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// ── Publishing ──────────────────────────────────────────────
//
// Android distribution is served by JitPack (https://jitpack.io), which
// clones the git tag and runs `publishToMavenLocal` (see jitpack.yml) to
// generate the artifacts on demand. No remote repository needs to be
// configured here — the KMP + maven-publish plugins produce the publications
// JitPack consumes.

group = "com.sparrowdesk"
version = sdkVersion

// ── XCFramework zip for SPM ─────────────────────────────────

tasks.register<Zip>("zipXCFramework") {
    dependsOn("assembleSparrowDeskSDKXCFramework")
    from(layout.buildDirectory.dir("XCFrameworks/release"))
    archiveFileName.set("SparrowDeskSDK.xcframework.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs"))
}

tasks.register("computeXCFrameworkChecksum") {
    dependsOn("zipXCFramework")
    doLast {
        val zipFile = layout.buildDirectory.file("outputs/SparrowDeskSDK.xcframework.zip").get().asFile
        val process = ProcessBuilder("swift", "package", "compute-checksum", zipFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val checksum = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        println("XCFramework checksum: $checksum")
        // Write checksum to file for CI usage
        layout.buildDirectory.file("outputs/checksum.txt").get().asFile.writeText(checksum)
    }
}
