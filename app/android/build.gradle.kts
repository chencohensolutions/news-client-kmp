plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apollo)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "news.app"
    compileSdk = 36

    defaultConfig {
        // Its own id, so it installs beside the older app that read the sites on the phone.
        applicationId = "news.client"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        // This machine's debug key, which signs the debug builds too: a release build then updates the installed app
        // in place and keeps its data (articles, settings). Its passwords are Android's public defaults. A
        // build for others would use a real key instead.
        create("local") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // The app for daily use: shrunk and optimized (R8). Compose runs several times faster than in the debug build,
        // which checks and traces as it goes. Not debuggable, so `adb run-as` can't read its files.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("local")
        }
        // The UI Lab app (scripts/ui-lab.sh): the debug build under its own id and name that only opens the lab, so it
        // installs beside the app without replacing it. Its code is src/debug's.
        create("uiLab") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uilab"
            matchingFallbacks += listOf("debug")
        }
    }
    sourceSets.named("uiLab") {
        kotlin.directories += "src/debug/kotlin"
    }

    buildFeatures {
        compose = true
    }

}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.coil.network.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.documentfile)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    implementation(libs.media3.transformer)
    implementation(libs.media3.muxer)
    // The server's video through OkHttp: the phone's certificate and the pinned server key on every media request.
    implementation(libs.media3.datasource.okhttp)
    // The invite's QR code (Settings › משתמשים).
    implementation(libs.zxing.core)

    // The news server (news-server-go): GraphQL over mutual TLS, and Google's code scanner for its enrollment QR code.
    implementation(libs.apollo.runtime)
    // The news server's DHT lookup (UDP), as the journal app's (news.app.server.discovery).
    implementation(libs.ktor.network)
    implementation(libs.play.services.code.scanner)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}

apollo {
    service("newsServer") {
        // The schema is news-server-go's graph/schema/*.graphql, copied as *.graphqls (./gradlew :app:android:syncServerSchema);
        // the operations the app sends sit next to it.
        srcDir("src/main/graphql")
        packageName.set("news.app.server.graphql")
        // RFC 3339 timestamps from gqlgen's Time scalar.
        mapScalarToKotlinString("Time")
    }
}

// Copies news-server-go's schema into src/main/graphql. The server checkout: -PnewsServerDir=<path>, else the sibling
// ../news-server-go. Apollo only reads *.graphqls as schema, so they are renamed.
val newsServerSchemaDir: File = rootProject.file((findProperty("newsServerDir") as String? ?: "../news-server-go") + "/graph/schema")
val checkServerSchemaDir = tasks.register("checkServerSchemaDir") {
    val dir = newsServerSchemaDir
    doLast { if (!dir.isDirectory) throw GradleException("Server schema not found at $dir. Pass -PnewsServerDir=<path to news-server-go>.") }
}
tasks.register<Copy>("syncServerSchema") {
    group = "apollo"
    description = "Copies the GraphQL schema from news-server-go into src/main/graphql."
    dependsOn(checkServerSchemaDir)
    from(newsServerSchemaDir) {
        include("*.graphql")
        rename { it.removeSuffix(".graphql") + ".graphqls" }
    }
    into(layout.projectDirectory.dir("src/main/graphql"))
}
