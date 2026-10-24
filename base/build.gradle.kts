import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tennis.base"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("11")
        }
    }
    buildFeatures {
        dataBinding = true
        viewBinding = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Hilt：api 传递给所有依赖 base 的宿主工程
    api(libs.hilt.android)
    ksp(libs.hilt.compiler)

    api(libs.bundles.lifecycle)
    api(libs.bundles.jetpack)
    api(libs.bundles.compose)
    api(libs.eventbus)
    api(libs.utilcodex)
    api(libs.ultimatebarx)

    api(libs.retrofit)
    api(libs.converter.gson)
    api(libs.retrofit2.kotlin.coroutines.adapter)
    api(libs.okhttp)
    api(libs.logging.interceptor)

    api(libs.glide)
    kapt(libs.compiler)
    api(libs.glide.transformations)
    api(libs.baserecyclerviewadapterhelper4)
    api(libs.zing)
    implementation("io.coil-kt:coil:2.6.0")
}
