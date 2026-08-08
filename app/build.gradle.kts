import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }
val apiAuthToken = localProps.getProperty("API_AUTH_TOKEN", "")
val remoteConfigUrl = localProps.getProperty("REMOTE_CONFIG_URL", "")
val releaseStoreFile = localProps.getProperty("RELEASE_STORE_FILE", "")
val releaseStorePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
val releaseKeyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
val releaseKeyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")

android {
    namespace = "com.ash.axis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ash.axis"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_AUTH_TOKEN", "\"$apiAuthToken\"")
        buildConfigField("String", "REMOTE_CONFIG_URL", "\"$remoteConfigUrl\"")

        // Axis ships an English-only UI; keep only English resources from libraries (AndroidX, Material,
        // CameraX, ML Kit) to trim the APK. Drop this if the app is ever localized.
        resourceConfigurations += "en"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile.isNotBlank()) {
                storeFile = file(releaseStoreFile)
            }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Ship only ARM native libraries in the distributed build. x86/x86_64 exist only for emulators —
            // dropping them removes ~12 MB of ML Kit's libbarhopper while keeping a single universal APK
            // (arm64 + 32-bit arm) for real phones. Debug stays universal for emulator development.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            // Use the real release keystore when configured; otherwise fall back to the debug key so local
            // `assembleRelease` still produces an installable APK. NOTE: over-the-air auto-updates require the
            // *same* signing key as the installed build, so distribute only builds signed with your real key.
            signingConfig =
                if (releaseStoreFile.isNotBlank()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeCompiler {
        stabilityConfigurationFile = project.layout.projectDirectory.file("compose-stability.conf")
        // Drops the wrapper group emitted around non-skipping composables — smaller codegen, less runtime work.
        featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
        // Opt-in recomposition metrics: ./gradlew :app:compileReleaseKotlin -PcomposeMetrics=true
        if (project.findProperty("composeMetrics") == "true") {
            metricsDestination = layout.buildDirectory.dir("compose_metrics")
            reportsDestination = layout.buildDirectory.dir("compose_metrics")
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt.android)
    implementation(libs.zxing.core)
    implementation(libs.mlkit.barcode)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.profileinstaller)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)

    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
