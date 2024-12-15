

buildscript {
    val kotlin_version = "1.8.0"

repositories {
google()
mavenCentral()
}
dependencies {
classpath("com.android.tools.build:gradle:8.7.3")
classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
}
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
