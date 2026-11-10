plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Penandatanganan rilis.
// Jika 4 variabel lingkungan di bawah diisi (mis. dari GitHub Secrets), APK/AAB
// rilis ditandatangani dengan keystore Anda. Jika tidak, build rilis memakai
// debug keystore supaya APK tetap bisa dipasang untuk uji coba.
// ---------------------------------------------------------------------------
val releaseStoreFile: String? = System.getenv("MUTQIN_KEYSTORE_PATH")
val releaseStorePassword: String? = System.getenv("MUTQIN_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("MUTQIN_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("MUTQIN_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

// versionCode otomatis naik di GitHub Actions (GITHUB_RUN_NUMBER), 1 jika build lokal.
val ciVersionCode: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
// versionName: dari tag rilis (mis. tag v1.1.0 -> "1.1.0"); build biasa diberi akhiran build agar mudah dibedakan.
val ciVersionName: String =
    System.getenv("GITHUB_REF_NAME")?.takeIf { Regex("""v\d+(\.\d+)*""").matches(it) }?.removePrefix("v")
        ?: "1.0.0-build$ciVersionCode"

android {
    namespace = "id.mutqin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.mutqin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Keystore debug TETAP (ikut di repo) supaya setiap APK dari GitHub Actions
        // bertanda tangan sama -> bisa dipasang di atas versi lama tanpa uninstall,
        getByName("debug") {
            storeFile = file("mutqin-debug.keystore")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }

    lint {
        // Jangan gagalkan build CI hanya karena peringatan lint.
        abortOnError = false
        checkReleaseBuilds = false
    }

    androidResources {
        // Klip audio petunjuk dibaca langsung dari assets lewat AssetFileDescriptor.
        noCompress += listOf("mp3", "webp")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Menerapkan baseline profile bawaan Compose saat pemasangan (startup dan scroll lebih mulus pada build rilis)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Pemeriksa cadangan pengingat (menyusulkan notifikasi yang terlewat)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    // org.json asli untuk unit test (android.jar hanya berisi stub).
    testImplementation("org.json:json:20240303")
}
