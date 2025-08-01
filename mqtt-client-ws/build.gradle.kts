import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation(libs.slf4j.api)
                implementation(libs.slf4j.simple)
            }
        }
    }
}

android {
    namespace = "de.kempmobil.ktor.mqtt.client.ws"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 21
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

mavenPublishing {
    coordinates("de.kempmobil.ktor.mqtt", "mqtt-client-ws", libs.versions.ktormqtt.get())
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGenerate"),
            sourcesJar = true,
            androidVariantsToPublish = listOf("debug", "release"),
        )
    )
}
