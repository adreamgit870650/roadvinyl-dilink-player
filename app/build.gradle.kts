plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val updateVersionUrl = providers.gradleProperty("ROADVINYL_UPDATE_VERSION_URL")
    .orElse(providers.environmentVariable("ROADVINYL_UPDATE_VERSION_URL"))
    .orElse("")

// Build one source tree for modern and legacy vehicle systems. The regular
// release keeps API 24, while local compatibility builds can opt into API 22
// or API 19 with the documented Gradle properties.
val requestedMinSdk = providers.gradleProperty("yinyanMinSdk").orNull?.toIntOrNull() ?: 22
require(requestedMinSdk in setOf(19, 22, 24)) {
    "yinyanMinSdk must be 19 (legacy car), 22 (Android 5.1), or 24 (Android 7.0)"
}
val requestedTargetSdk = providers.gradleProperty("yinyanTargetSdk").orNull?.toIntOrNull() ?: 34
require(requestedTargetSdk in setOf(23, 34)) {
    "yinyanTargetSdk must be 23 (legacy car) or 34 (regular release)"
}
require(requestedTargetSdk >= requestedMinSdk) { "yinyanTargetSdk must not be lower than yinyanMinSdk" }
val requestedReleaseMinify = providers.gradleProperty("yinyanReleaseMinify").orNull?.toBooleanStrictOrNull() ?: false

android {
    namespace = "com.car.mp3player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.car.mp3player"
        minSdk = requestedMinSdk
        targetSdk = requestedTargetSdk
        versionCode = 47
        versionName = "4.7.0"
        buildConfigField(
            "String",
            "UPDATE_VERSION_URL",
            buildConfigString(updateVersionUrl.get()),
        )
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = requestedReleaseMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.transition:transition:1.4.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.belerweb:pinyin4j:2.5.1")
    testImplementation("junit:junit:4.13.2")
}
