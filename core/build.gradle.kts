import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: command understanding, planning and app matching.
// It has no Android dependency so it builds and tests on any JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
