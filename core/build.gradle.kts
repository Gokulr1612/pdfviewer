plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately an Android-free module. Everything here must compile and be
// testable on a plain JVM so the format logic can be unit tested without a
// device or emulator. Android supplies org.xmlpull.v1 on its bootclasspath;
// on the JVM the tests pull in kxml2 to provide the same API.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The XmlPullParser *API* only. Android supplies an implementation on its
    // bootclasspath; the tests below supply kxml2's. Nothing from this
    // dependency is bundled, which is what keeps the module Android-free.
    compileOnly(libs.kxml2)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
