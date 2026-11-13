plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.lighttube.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lighttube.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 10000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    flavorDimensions += "channel"
    productFlavors {
        create("dev") {
            dimension = "channel"
            applicationIdSuffix = ".dev"
        }
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
        }
        create("prod") { dimension = "channel" }
    }

    val signingEnvironment = mapOf(
        "storeFile" to "LIGHTTUBE_RELEASE_KEYSTORE",
        "storePassword" to "LIGHTTUBE_RELEASE_STORE_PASSWORD",
        "keyAlias" to "LIGHTTUBE_RELEASE_KEY_ALIAS",
        "keyPassword" to "LIGHTTUBE_RELEASE_KEY_PASSWORD",
    ).mapValues { (_, name) -> providers.environmentVariable(name).orNull }

    if (signingEnvironment.values.all { !it.isNullOrBlank() }) {
        signingConfigs.create("production") {
            storeFile = file(checkNotNull(signingEnvironment["storeFile"]))
            storePassword = signingEnvironment["storePassword"]
            keyAlias = signingEnvironment["keyAlias"]
            keyPassword = signingEnvironment["keyPassword"]
        }
    }
    buildTypes {
        create("internal") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            applicationIdSuffix = ".internal"
        }
        release {
            signingConfig = signingConfigs.findByName("production")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }

dependencies {
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.media3.session)
    implementation(libs.core)
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.material)
    implementation(libs.documentfile)
    implementation(libs.multidex)
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    listOf(
        "explore", "download", "player", "search", "vault", "clean", "boost",
        "whatsapp", "web", "taskmanager", "settings", "updater", "ads", "push", "analytics", "oem",
    ).forEach { implementation(project(":feature:$it")) }
}
