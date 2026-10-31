import java.util.Properties
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.instrumentation.FramesComputationMode
import org.dergigi.feather.buildlogic.DetachedSemanticsGuard
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val secrets = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String? = System.getenv(name) ?: secrets.getProperty(name)

val gifApiKey = secret("GIFS_NOSTR_BUILD_API_KEY").orEmpty().also {
    require(it.isEmpty() || it.matches(Regex("gnb_[A-Za-z0-9_-]+"))) { "Invalid GIF API key format" }
}

val gitCommit = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.get().trim().also {
    require(it.matches(Regex("[0-9a-f]{40,64}"))) { "Cannot determine build commit" }
}

android {
    namespace = "org.dergigi.feather"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.dergigi.feather"
        minSdk = 26
        targetSdk = 37
        versionCode = 233
        versionName = "1.10.0"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "GIFS_NOSTR_BUILD_API_KEY", "\"$gifApiKey\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (secret("OEM_STORE_FILE") != null) {
        signingConfigs.create("release") {
            storeFile = file(secret("OEM_STORE_FILE")!!)
            storePassword = secret("OEM_STORE_PASSWORD")
            keyAlias = secret("OEM_KEY_ALIAS")
            keyPassword = secret("OEM_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true; aidl = true }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.vitorpamplona.quartz:quartz:1.17.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.emoji2:emoji2-emojipicker:1.7.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jsoup:jsoup:1.23.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-android:0.35.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.35.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3")
    implementation("io.coil-kt.coil3:coil-svg:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm:0.24.0")
}

// Material 3 1.4.0 detached-node crash; see docs/COMPOSE_WORKAROUND.md.
androidComponents.onVariants { variant ->
    variant.instrumentation.transformClassesWith(DetachedSemanticsGuard::class.java, InstrumentationScope.ALL) {}
    variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
}
