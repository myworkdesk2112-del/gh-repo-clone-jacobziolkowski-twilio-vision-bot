import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.giva.hiassist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.giva.hiassist"
        minSdk = 31
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "BHASHINI_BASE_URL", "\"${project.findProperty("BHASHINI_BASE_URL") ?: "wss://tts.bhashini.ai/stt/stream"}\"")
        buildConfigField("String", "BHASHINI_API_KEY", "\"${project.findProperty("BHASHINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "BHASHINI_PIPELINE_ID", "\"${project.findProperty("BHASHINI_PIPELINE_ID") ?: ""}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }

    androidResources {
        noCompress += listOf("onnx", "txt")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

val sravaaniAssetDir = layout.projectDirectory.dir("src/main/assets/sravaani")
val sravaaniModel = sravaaniAssetDir.file("model-la13.onnx")
val sravaaniTokens = sravaaniAssetDir.file("tokens.txt")

val downloadSravaaniModel by tasks.registering {
    group = "hiassist"
    description = "Downloads the pinned SraVaani-0.5-live int8 ONNX model and tokenizer into APK assets."
    outputs.files(sravaaniModel, sravaaniTokens)

    doLast {
        val dir = sravaaniAssetDir.asFile
        dir.mkdirs()
        val model = sravaaniModel.asFile
        val tokens = sravaaniTokens.asFile

        fun fetchIfMissing(url: String, destination: File, expectedBytes: Long) {
            if (destination.exists() && destination.length() == expectedBytes) return

            val maxAttempts = 5
            var lastError: Exception? = null
            for (attempt in 1..maxAttempts) {
                try {
                    val alreadyHave = if (destination.exists()) destination.length() else 0L
                    val resuming = alreadyHave in 1 until expectedBytes
                    println(
                        "Downloading ${destination.name} (${expectedBytes} bytes total, attempt $attempt/$maxAttempts" +
                            (if (resuming) ", resuming from $alreadyHave" else "") + ")..."
                    )
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 30_000
                        readTimeout = 60_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "GiVaHiAssist-Gradle/1.0")
                        if (resuming) setRequestProperty("Range", "bytes=$alreadyHave-")
                    }
                    connection.connect()
                    val ok = connection.responseCode in 200..299
                    check(ok) { "HTTP ${connection.responseCode} fetching ${destination.name}" }
                    val append = resuming && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
                    if (!append && destination.exists()) destination.delete()
                    connection.inputStream.buffered().use { input ->
                        FileOutputStream(destination, append).buffered().use { output -> input.copyTo(output) }
                    }
                    check(destination.length() == expectedBytes) {
                        "Unexpected ${destination.name} size: ${destination.length()} (expected $expectedBytes)"
                    }
                    return
                } catch (e: Exception) {
                    lastError = e
                    println("Attempt $attempt for ${destination.name} failed: ${e.message}")
                    if (attempt < maxAttempts) Thread.sleep(2_000L * attempt)
                }
            }
            throw GradleException(
                "Failed to download ${destination.name} after $maxAttempts attempts", lastError
            )
        }

        fetchIfMissing(
            "https://huggingface.co/mobilebytesensei/betterflow-sravaani-streaming-onnx/resolve/main/model-la13.onnx?download=true",
            model,
            658_699_885L
        )
        fetchIfMissing(
            "https://huggingface.co/mobilebytesensei/betterflow-sravaani-streaming-onnx/resolve/main/tokens.txt?download=true",
            tokens,
            68_907L
        )
    }
}

tasks.named("preBuild").configure { dependsOn(downloadSravaaniModel) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Local streaming ASR runtime (includes Android JNI/native libraries).
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json stubs throw on every call in a plain JVM unit test;
    // this pulls in the real implementation so BhashiniSttEngine's JSON
    // parsing can be tested without Robolectric/instrumentation.
    testImplementation("org.json:json:20240303")
}
