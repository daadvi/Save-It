plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.saveit"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.saveit"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.16.0"
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

val webAssets = tasks.register<Sync>("syncWebAssets") {
    from(providers.gradleProperty("saveItWebHtml").map { rootProject.file(it) }
        .orElse(rootProject.file("desktop/Save-It.html")))
    rename { "Save-It.html" }
    into(layout.buildDirectory.dir("generated/webAssets"))
}
android.sourceSets["main"].assets.srcDir(webAssets.map { it.destinationDir })
tasks.named("preBuild").configure { dependsOn(webAssets) }
