import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// MedLog is signed with the same key as Meeting Timer so the two apps can share a
// signature-protected local bridge (plan D5). The key lives in the Meeting Timer repo.
val keystoreDir = rootProject.file("../Google calendar timer/android/keystore")
val keystore = Properties().apply {
    val f = File(keystoreDir, "keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// On GitHub (release workflow) the key comes from environment variables instead.
System.getenv("MEDLOG_STORE_FILE")?.let { f ->
    keystore.setProperty("storeFile", f)
    keystore.setProperty("storePassword", System.getenv("MEDLOG_STORE_PASSWORD"))
    keystore.setProperty("keyAlias", System.getenv("MEDLOG_KEY_ALIAS"))
    keystore.setProperty("keyPassword", System.getenv("MEDLOG_KEY_PASSWORD"))
}
val hasKey = keystore.getProperty("storePassword") != null

android {
    namespace = "com.suryaprakash.medlog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.suryaprakash.medlog"
        minSdk = 23
        targetSdk = 35
        versionCode = 2101
        versionName = "2.10.1"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasKey) create("shared") {
            storeFile = keystore.getProperty("storeFile", "meeting-timer.jks").let { f -> File(f).takeIf { it.isAbsolute } ?: File(keystoreDir, f) }
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKey) signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            // same key as release, so a debug build can be upgraded to release without losing data
            if (hasKey) signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // the speech model is already compressed; don't let aapt try again
    androidResources { noCompress += listOf("mdl", "fst", "int", "conf", "mat", "ie", "dubm", "stats") }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // scripts/shots.sh passes which screens to draw
        unitTests.all { t ->
            listOf("shots", "shots.role", "shots.tall", "shots.lang", "shots.big").forEach { k -> System.getProperty(k)?.let { t.systemProperty(k, it) } }
            t.systemProperty("shots.dir", layout.buildDirectory.dir("shots").get().asFile.absolutePath)
            t.maxHeapSize = "3g"
            // screenshots load Robolectric's native graphics; only when asked for, never in ordinary test runs
            if (System.getProperty("shots") == null) t.exclude("**/Shots*")
        }
    }
    // One APK per phone type keeps the download small (the speech engine is native code).
    // arm64-v8a: almost all phones from 2017 on; armeabi-v7a: older/cheaper phones; x86_64: emulator.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // encrypted local database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // home-screen widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // offline speech recognition

    // offline text recognition (medicine strips, old reports) — bundled model, no download
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // nearby helper phones, no internet (far-away phones use help/Relay.kt, plain HttpURLConnection)
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // screenshots of real screens on the computer (scripts/shots.sh)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
