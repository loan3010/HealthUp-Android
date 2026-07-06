plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

fun extractGoogleWebClientId(googleServicesFile: java.io.File, applicationId: String): String {
    if (!googleServicesFile.exists()) return ""
    val text = googleServicesFile.readText()
    val packageMarker = "\"package_name\": \"$applicationId\""
    val packageIndex = text.indexOf(packageMarker)
    if (packageIndex < 0) return ""

    val oauthStart = text.indexOf("\"oauth_client\"", packageIndex)
    if (oauthStart < 0) return ""

    val oauthArrayStart = text.indexOf('[', oauthStart)
    val oauthArrayEnd = text.indexOf(']', oauthArrayStart)
    if (oauthArrayStart < 0 || oauthArrayEnd < 0) return ""

    val oauthSection = text.substring(oauthArrayStart, oauthArrayEnd)
    if (oauthSection.isBlank() || oauthSection == "[]") return ""

    val clientIdRegex = Regex("\"client_id\"\\s*:\\s*\"([^\"]+)\"")
    val typeRegex = Regex("\"client_type\"\\s*:\\s*(\\d+)")

    var searchFrom = 0
    while (searchFrom < oauthSection.length) {
        val typeMatch = typeRegex.find(oauthSection, searchFrom) ?: break
        if (typeMatch.groupValues[1] == "3") {
            val beforeType = oauthSection.substring(0, typeMatch.range.first)
            val clientIdMatch = clientIdRegex.findAll(beforeType).lastOrNull()
            if (clientIdMatch != null) {
                return clientIdMatch.groupValues[1]
            }
        }
        searchFrom = typeMatch.range.last + 1
    }

    return clientIdRegex.find(oauthSection)?.groupValues?.get(1).orEmpty()
}

android {
    namespace = "com.example.healthup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.healthup"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val webClientId = extractGoogleWebClientId(file("google-services.json"), applicationId!!)
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.splashscreen)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-functions")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment:2.7.6")
    implementation("androidx.navigation:navigation-ui:2.7.6")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")

    // Social login
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.facebook.android:facebook-login:17.0.2")

    // Flexbox (checkout UI from TH)
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
