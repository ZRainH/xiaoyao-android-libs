import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

val bluetoothVersion = "1.0.0"

fun resolveConfig(propName: String, vararg envNames: String): String? {
    envNames.forEach { env ->
        System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return (findProperty(propName) as String?)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "cn.xiaoyao.bluetooth"
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
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
    sourceSets {
        getByName("main") {
            res {
                srcDirs("src\\main\\res", "src\\main\\res")
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "cn.xiaoyao"
                artifactId = "bluetooth"
                version = bluetoothVersion

                from(components["release"])

                pom {
                    name.set("Bluetooth BLE Library")
                    description.set("Multi-device BLE library with auto-connect and protocol parsing")
                }
            }
        }
        repositories {
            maven {
                name = "local"
                url = uri(rootProject.file("local-maven-repo"))
            }
            val githubOwner = resolveConfig("gpr.repo.owner", "GPR_REPO_OWNER")
            val githubRepo = resolveConfig("gpr.repo.name", "GPR_REPO_NAME")
            logger.lifecycle("GitHub Packages config: owner=$githubOwner, repo=$githubRepo")
            if (!githubOwner.isNullOrBlank() && !githubRepo.isNullOrBlank()) {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/$githubOwner/$githubRepo")
                    credentials {
                        username = resolveConfig("gpr.user", "GITHUB_ACTOR").orEmpty()
                        password = resolveConfig("gpr.key", "GITHUB_TOKEN").orEmpty()
                    }
                }
            }
        }
    }
}