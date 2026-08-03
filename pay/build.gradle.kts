import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.maven.publish)
    signing
}

val payVersion = "1.0.1"

fun resolveConfig(propName: String, vararg envNames: String): String? {
    envNames.forEach { env ->
        System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return (findProperty(propName) as String?)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.xiaoyao.pay"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

dependencies {
    // 银联本地 jar（打进 AAR）
    implementation(files("libs/UPPayAssistEx_3.4.6.jar"))
    implementation(files("libs/UPPayPluginExPro_3.4.6.jar"))

    api(libs.alipay.sdk.android)
    api(libs.wechat.sdk.android)

    implementation(libs.kotlinx.coroutines.android)
}

mavenPublishing {
    signAllPublications()
    coordinates("io.github.zrainh", "pay", payVersion)
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Xiaoyao Pay Utils")
        description.set("Android payment helper for Alipay, WeChat Pay and UnionPay")
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
