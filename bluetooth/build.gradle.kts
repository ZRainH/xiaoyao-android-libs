import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.maven.publish)
    signing
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

mavenPublishing {
    // 通过 gradle.properties 的 SONATYPE_HOST=CENTRAL_PORTAL 启用 Central Portal
    signAllPublications()

    // 坐标：需与 Central 已验证的 namespace 一致
    coordinates("io.github.zrainh", "bluetooth", bluetoothVersion)

    // javadoc 由插件生成（Android 下多为空 jar，满足 Central 要求）
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))

    pom {
        name.set("Bluetooth BLE Library")
        description.set("Multi-device BLE library with auto-connect and protocol parsing")
        inceptionYear.set("2026")
        url.set("https://github.com/ZRainH/AndroidBluetooth")
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
            url.set("https://github.com/ZRainH/AndroidBluetooth")
            connection.set("scm:git:git://github.com/ZRainH/AndroidBluetooth.git")
            developerConnection.set("scm:git:ssh://git@github.com/ZRainH/AndroidBluetooth.git")
        }
    }
}

// 现代 GPG 无 secring.gpg，改用 gpg 命令行签名
extensions.configure<org.gradle.plugins.signing.SigningExtension>("signing") {
    useGpgCmd()
}

// 可选：继续发布到本地 / GitHub Packages
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
