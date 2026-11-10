plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.openkeys"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "io.github.openkeys"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    // Release signing comes only from environment variables (CI secrets); never from the repo.
    val keystorePath = providers.environmentVariable("OPENKEYS_KEYSTORE").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("OPENKEYS_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("OPENKEYS_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("OPENKEYS_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

abstract class CollectLicenses : DefaultTask() {
    @get:InputFiles
    abstract val licenseFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun collect() {
        val dir = outputDir.get().asFile.resolve("licenses")
        dir.deleteRecursively()
        dir.mkdirs()
        licenseFiles.forEach { f ->
            f.copyTo(dir.resolve(if (f.name == "LICENSE") "OpenKeys-Apache-2.0.txt" else f.name), overwrite = true)
        }
    }
}

val collectLicenses = tasks.register<CollectLicenses>("collectLicenses") {
    licenseFiles.from(rootProject.file("LICENSE"), rootProject.file("THIRD_PARTY_NOTICES.md"), rootProject.fileTree("licenses"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(collectLicenses, CollectLicenses::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kshoji.midi.driver)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
