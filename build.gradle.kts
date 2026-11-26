plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    kotlin("jvm")
}
dependencies {
    testImplementation(kotlin("test"))
}
kotlin {
    jvmToolchain(8)
}