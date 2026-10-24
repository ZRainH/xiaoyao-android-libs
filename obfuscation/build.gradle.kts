import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.maven.publish)
    signing
}

val obfuscationVersion = "1.0.0"

fun resolveConfig(propName: String, vararg envNames: String): String? {
    envNames.forEach { env ->
        System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return (findProperty(propName) as String?)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.xiaoyao.obfuscation"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles(
            "proguard/00-common.pro",
            "proguard/10-kotlin.pro",
            "proguard/20-coroutines.pro",
            "proguard/30-androidx.pro",
            "proguard/35-android-ui.pro",
            "proguard/40-okhttp-retrofit.pro",
            "proguard/50-gson.pro",
            "proguard/60-glide.pro",
            "proguard/70-eventbus.pro",
            "proguard/80-hilt.pro"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
}

mavenPublishing {
    signAllPublications()
    coordinates("io.github.zrainh", "obfuscation", obfuscationVersion)
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Xiaoyao Obfuscation")
        description.set("Shared ProGuard/R8 consumer rules for Android apps")
        inceptionYear.set("2026")
        url.set("https://github.com/ZRainH/xiaoyao-android-libs")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ZRainH")
                name.set("逍遥")
                url.set("https://github.com/ZRainH")
            }
        }
        scm {
            url.set("https://github.com/ZRainH/xiaoyao-android-libs")
            connection.set("scm:git:git://github.com/ZRainH/xiaoyao-android-libs.git")
            developerConnection.set("scm:git:ssh://git@github.com/ZRainH/xiaoyao-android-libs.git")
        }
    }
}

extensions.configure<org.gradle.plugins.signing.SigningExtension>("signing") {
    useGpgCmd()
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "local"
                url = uri(rootProject.file("local-maven-repo"))
            }
            val githubOwner = resolveConfig("gpr.repo.owner", "GPR_REPO_OWNER")
            val githubRepo = resolveConfig("gpr.repo.name", "GPR_REPO_NAME")
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
