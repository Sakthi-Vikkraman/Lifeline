import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.lifeline.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lifeline.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Left unsigned on purpose: `signedRelease` signs it with the release key and the
            // key-rotation lineage from the original (debug) key. See CLAUDE.md, "Release signing".
            signingConfig = null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.6.0")
}

// ----- Release signing -----------------------------------------------------------------------
// v1.0.0 and v1.1.0 were signed with the original debug key. Releases are now signed with the
// release key *and* a lineage proving the old key handed over to it (APK Signature Scheme v3
// rotation). Android 9+ verifies the new key; older devices verify the old key through the v1/v2
// signatures. That's why both keystores are needed for every release. Secrets live in the
// git-ignored keystore.properties.
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

tasks.register<Exec>("signedRelease") {
    group = "build"
    description = "Builds the release APK and signs it with the release key plus the key-rotation lineage."
    dependsOn("assembleRelease")

    val unsigned = layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk").get().asFile
    val out = layout.buildDirectory.file("outputs/apk/release/Lifeline-v${android.defaultConfig.versionName}.apk").get().asFile
    val windows = System.getProperty("os.name").startsWith("Windows")
    val apksigner = File(android.sdkDirectory, "build-tools/${android.buildToolsVersion}/apksigner" + if (windows) ".bat" else "")
    fun prop(key: String) = signingProps.getProperty(key, "")

    doFirst {
        check(!signingProps.isEmpty) { "keystore.properties is missing; release signing isn't set up on this machine." }
        check(File(prop("lineageFile")).exists()) { "Key-rotation lineage not found at ${prop("lineageFile")}." }
    }
    environment("LIFELINE_LEGACY_PASS", prop("legacyStorePassword"))
    environment("LIFELINE_LEGACY_KEY_PASS", prop("legacyKeyPassword"))
    environment("LIFELINE_STORE_PASS", prop("storePassword"))
    environment("LIFELINE_KEY_PASS", prop("keyPassword"))
    commandLine(
        apksigner, "sign",
        "--ks", prop("legacyStoreFile"), "--ks-key-alias", prop("legacyKeyAlias"),
        "--ks-pass", "env:LIFELINE_LEGACY_PASS", "--key-pass", "env:LIFELINE_LEGACY_KEY_PASS",
        "--next-signer",
        "--ks", prop("storeFile"), "--ks-key-alias", prop("keyAlias"),
        "--ks-pass", "env:LIFELINE_STORE_PASS", "--key-pass", "env:LIFELINE_KEY_PASS",
        "--lineage", prop("lineageFile"),
        "--rotation-min-sdk-version", "28",
        "--out", out.path,
        unsigned.path,
    )
    doLast { println("Signed release: ${out.path}") }
}
