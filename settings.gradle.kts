pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx publishes the Android AAR through its official JitPack setup.
        // Runtime networking is unaffected: this repository is build-time only.
        maven("https://jitpack.io")
    }
}

rootProject.name = "OrbitImeAndroid"
include(":app")
