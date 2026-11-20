plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.superdialer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.superdialer"
        minSdk = 29
        targetSdk = 36
        // CI sets these from the release tag (v1.2.3 -> "1.2.3") and the run number; local builds use the defaults.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
        testInstrumentationRunner = "com.example.superdialer.SuperDialerTestRunner"
        // T map (SK open API) app key for the Map section. From the TMAP_APP_KEY environment variable (CI) or, locally, a
        // TMAP_APP_KEY=... line in local.properties (git-ignored). Never committed. Without it the Map section shows a notice.
        val tmapKey = System.getenv("TMAP_APP_KEY")?.trim()?.takeIf { it.isNotEmpty() }
            ?: rootProject.file("local.properties").takeIf { it.exists() }?.readLines()
                ?.firstOrNull { it.startsWith("TMAP_APP_KEY=") }?.removePrefix("TMAP_APP_KEY=")?.trim()
            ?: ""
        buildConfigField("String", "TMAP_APP_KEY", "\"$tmapKey\"")
    }

    // Release signing comes from the environment (GitHub Secrets in CI). Without KEYSTORE_FILE no signing
    // config is created and `assembleRelease` produces an unsigned APK instead of failing.
    val keystoreFile = System.getenv("KEYSTORE_FILE")
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // `./gradlew assembleDebug -PphoneDebug` builds a copy that installs NEXT TO the real app (other package name and
        // label, own data) so it can be tried on the phone over USB cable without touching the installed release.
        debug {
            if (project.hasProperty("phoneDebug")) {
                applicationIdSuffix = ".dev"
            }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8: shrinks code and resources and renames classes (rules in proguard-rules.pro). Only the release build is
            // minified, so every bug it could cause shows up only there: check a release build on a fresh install.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    if (project.hasProperty("phoneDebug")) {
        sourceSets.getByName("debug").res.srcDir("src/phonedebug/res") // the "SuperDialer Dev" name
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
