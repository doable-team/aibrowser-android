plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.mrbean.aibrowser"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.mrbean.aibrowser"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Android unit tests compile with `-no-jdk`, so the jdk.httpserver module is
// not on the compile classpath. Package its classes into a jar the tests can
// use, since com.sun.net.httpserver ships only inside the JDK module.
val jdkHttpserverJar by tasks.registering(Jar::class) {
    archiveFileName.set("jdk-httpserver.jar")
    destinationDirectory.set(layout.buildDirectory.dir("test-libs"))
    from(zipTree(file("${System.getProperty("java.home")}/jmods/jdk.httpserver.jmod"))) {
        include("classes/**")
    }
    eachFile {
        if (relativePath.pathString.startsWith("classes/")) {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
}

tasks.configureEach {
    if (name == "compileDebugUnitTestKotlin" || name == "testDebugUnitTest") {
        dependsOn(jdkHttpserverJar)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(files(layout.buildDirectory.file("test-libs/jdk-httpserver.jar")))
}