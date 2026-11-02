import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localConfig = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun mapCredential(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name)).orElse(localConfig.getProperty(name, "")).get()
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    buildFeatures { buildConfig = true }
    namespace = "com.example.coletai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"" + providers.gradleProperty("COLETAI_API_BASE_URL").orElse("").get() + "\"")
        buildConfigField("String", "MAPTILER_API_KEY", quoted(mapCredential("COLETAI_MAPTILER_API_KEY")))
        buildConfigField("String", "ORS_API_KEY", quoted(mapCredential("COLETAI_ORS_API_KEY")))
        applicationId = "com.example.coletai"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    implementation(libs.viewpager2)
    implementation(libs.recyclerview)
    implementation(libs.maplibre)

    implementation(libs.splashscreen)

    testImplementation(libs.junit)
    // A implementação real de JSON permite testar persistência e protocolo na JVM.
    testImplementation(libs.json.test)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
