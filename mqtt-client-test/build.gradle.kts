import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mockery)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvm()
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    if (project.hasProperty("enableIos")) {
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
        jvmTest {
            dependencies {
                implementation(projects.mqttCore)
                implementation(projects.mqttClient)
                implementation(projects.mqttClientWs)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.client.java)
                implementation(libs.ktor.client.logging)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)
                implementation(libs.junit.api)
                implementation(libs.junit.engine)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junit)
                implementation(libs.slf4j.api)
                implementation(libs.slf4j.simple)
            }
        }
    }
}

android {
    namespace = "de.kempmobil.ktor.mqtt.client.test"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}
