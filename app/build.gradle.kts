plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.regepower.switchdns"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.regepower.switchdns"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Release key from CI secrets. Only KEYSTORE_BASE64 + KEYSTORE_PASSWORD are required: without
    // KEY_ALIAS the first alias in the keystore is used, without KEY_PASSWORD the store password.
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    if (keystorePath != null) {
        val storePw = System.getenv("KEYSTORE_PASSWORD").orEmpty()
        val alias =
            System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
                ?: java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).run {
                    file(keystorePath).inputStream().use { load(it, storePw.toCharArray()) }
                    aliases().nextElement()
                }
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = storePw
                keyAlias = alias
                keyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: storePw
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without release secrets, sign with the debug key so the APK stays installable.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Size (measured): deflate classes.dex in the APK (AGP stores it uncompressed for minSdk >= 28): -116 KB;
    // drop unused Kotlin builtins/metadata resources: -13 KB.
    packaging {
        dex { useLegacyPackaging = true }
        resources {
            excludes += setOf("kotlin/**", "kotlin-tooling-metadata.json", "META-INF/*.version")
        }
    }

    lint {
        abortOnError = true
        textReport = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
