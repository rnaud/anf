plugins { id("com.android.application") }
android {
    namespace = "io.anf.games"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.anf.games"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val keystore = System.getenv("ANF_KEYSTORE")
    if (keystore != null) signingConfigs.create("release") {
        storeFile = file(keystore)
        storePassword = System.getenv("ANF_KEYSTORE_PASSWORD")
        keyAlias = "anf"
        keyPassword = System.getenv("ANF_KEYSTORE_PASSWORD")
    }
    buildTypes { getByName("release") { if (keystore != null) signingConfig = signingConfigs.getByName("release") } }
}
dependencies { testImplementation("junit:junit:4.13.2") }
