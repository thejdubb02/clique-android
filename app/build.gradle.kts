import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
  Release signing, from a file that is deliberately not in this repo.

  Android ties an installed app to the key that signed it. Every release,
  including the ones our own F-Droid repo serves, is signed by the keystore at
  the path below, and that keystore is permanent: replacing it means every
  existing install has to be removed and reinstalled by hand. It is backed up
  in Vaultwarden.

  A clone without that file still builds. It falls back to the debug key, which
  is right for someone trying the thing out and wrong for anything published,
  so `signedRelease` below is what the publish script checks.
*/
val signingProps = Properties().apply {
    val f = file(System.getenv("CLIQUE_SIGNING_PROPERTIES") ?: "/root/.clique-android/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val signedRelease = signingProps.containsKey("storeFile")

android {
    namespace = "dev.useclique.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.useclique.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.5"
    }

    signingConfigs {
        if (signedRelease) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signedRelease) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        allWarningsAsErrors = false
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// xterm.js UMD bundles are built from source (tools/build-xterm.sh) and must
// not be committed. Run that script only when they are missing so a later
// `./gradlew --offline assembleDebug` never touches the network.
val xtermVendorJs = listOf(
    "xterm.js",
    "addon-fit.js",
    "addon-unicode11.js",
    "addon-canvas.js",
).map { file("src/main/assets/vendor/$it") }

val buildXtermIfMissing = tasks.register<Exec>("buildXtermIfMissing") {
    group = "build"
    description = "Build xterm.js from source when vendor JS is missing"
    workingDir = rootProject.projectDir
    commandLine("bash", "tools/build-xterm.sh")
    onlyIf { xtermVendorJs.any { !it.exists() } }
}

tasks.configureEach {
    if (name == "mergeDebugAssets" || name == "mergeReleaseAssets") {
        dependsOn(buildXtermIfMissing)
    }
}

dependencies {
    // AndroidX platform pieces. No Play Services, no Firebase, no analytics.
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Token store. Spec names this library. Stable 1.0.0, Apache-2.0, F-Droid-clean.
    implementation("androidx.security:security-crypto:1.0.0")

    // HTTP plus WebSocket. HttpURLConnection cannot do /ws; one OkHttp client
    // also carries the per-server private-CA trust store for both REST and the
    // terminal stream. No Retrofit.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JVM unit tests. Test classpath only; not packaged.
    testImplementation("junit:junit:4.13")
}
