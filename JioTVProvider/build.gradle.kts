import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("com.android.tools.build:gradle:8.7.3")
    }
}

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3.gradle")

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

cloudstream {
    // Update to your own repo URL if you fork/publish this
    setRepo("https://github.com/Subrata96411/JioTVProvider")

    authors = listOf("Subrata96411")
    description = "Watch JioTV live channels in CloudStream. Requires a Jio mobile number for OTP login."
    language = "all"
    status = 1 // 1 = Working
}

android {
    namespace = "com.example.jiotvprovider"
    compileSdkVersion(35)

    defaultConfig {
        minSdk = 21
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val cloudstream: Configuration by configurations
    cloudstream("com.github.recloudstream.cloudstream:library:-SNAPSHOT")

    implementation(kotlin("stdlib"))
    // Gson for JSON serialization / deserialization
    implementation("com.google.code.gson:gson:2.10.1")
    // OkHttp is bundled by CloudStream but referenced here for clarity
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
}
