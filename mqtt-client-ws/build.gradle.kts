@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mockery)
    alias(libs.plugins.kover)
    alias(libs.plugins.vanniktech)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    jvm()
    androidTarget {
        compilations {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
            }
        }
        publishLibraryVariants("release", "debug")
    }

    if (System.getenv("INCLUDE_IOS")?.toBoolean() == true) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach {
            it.binaries.framework {
                baseName = "base"
                isStatic = true
            }
        }
    }
    if (System.getenv("INCLUDE_WASM")?.toBoolean() == true) {
        wasmJs {
            binaries.executable()
            browser() {
                commonWebpackConfig {
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static(project.rootDir.path)
                    }
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.mqttCore)
                api(projects.mqttClient)
                api(libs.ktor.client.websockets)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation(libs.slf4j.api)
                implementation(libs.slf4j.simple)
            }
        }
        if (System.getenv("INCLUDE_WASM")?.toBoolean() == true) {
            wasmJsMain {
                dependencies {
                    implementation(libs.kotlinx.browser)
                }
            }
        }
    }
}

android {
    namespace = "de.kempmobil.ktor.mqtt.client.ws"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

dokka {
    moduleName.set("Ktor-MQTT Websocket Client v${libs.versions.ktormqtt.get()}")
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(projectDir.resolve("src"))
            remoteUrl.set(URI("https://github.com/ukemp/ktor-mqtt/tree/main/mqtt-client-ws/src"))
            remoteLineSuffix.set("#L")
        }
    }
}

// Do not delete nor move to the root script, otherwise iOS artifact will miss these values
group = "de.kempmobil.ktor.mqtt"
version = libs.versions.ktormqtt.get()

mavenPublishing {
    // It's not sufficient to call coordinates() here, group and version must also be defined as above
    coordinates(group.toString(), "mqtt-client-ws", version.toString())
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGenerate"),
            sourcesJar = true,
            androidVariantsToPublish = listOf("debug", "release"),
        )
    )
}
