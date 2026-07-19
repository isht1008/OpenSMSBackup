plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.legacy.kapt)
}

android {

    namespace = "io.github.isht1008.opensmsbackup"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {

        applicationId = "io.github.isht1008.opensmsbackup"
        minSdk = 31
        targetSdk = 36

        versionCode = 3
        versionName = "0.3.0"

        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"47992254434-2p7kh6eb9kb8d60t83041hc78cs6vmgc.apps.googleusercontent.com\""
        )

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {

            annotationProcessorOptions {

                arguments += mapOf(

                    "room.schemaLocation" to
                            "$projectDir/schemas",

                    "room.incremental" to
                            "true",

                    "room.expandProjection" to
                            "true"

                )

            }

        }

    }

    buildTypes {

        release {

            optimization {
                enable = false
            }

        }

    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11

    }

    buildFeatures {

        compose = true
        buildConfig = true

    }

    packaging {

        resources {

            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"

        }

    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

}

kapt {

    correctErrorTypes = true

}

dependencies {

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation("androidx.navigation:navigation-compose:2.9.3")

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.credentials)

    implementation(libs.androidx.credentials.play.services.auth)

    implementation(libs.googleid)

    implementation(libs.play.services.auth)

    implementation(libs.google.api.client.android)

    implementation(libs.google.http.client.android)

    implementation(libs.google.api.services.gmail)

    implementation(libs.google.api.client.android.extensions)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)

    implementation(libs.androidx.room.ktx)

    implementation(libs.androidx.work.runtime.ktx)

    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        "androidx.room:room-testing:2.8.0"
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

}
