plugins {
  id("com.android.application")
}

// A small app that runs the Android backend on a real device: a triangle drawn off screen and into a SurfaceView, a
// check that rendering calls allocate nothing, and a measurement of what a call costs. Results go to logcat under the
// tag "jwebgpu". The sample is not published.
android {
  namespace = "io.github.stringdotjar.jwebgpu.samples.android"
  compileSdk = 36

  defaultConfig {
    applicationId = "io.github.stringdotjar.jwebgpu.samples.android"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1"
  }

  buildTypes {
    release {
      // A release build is what matters for timings: it is not debuggable. R8 runs too, which proves that the
      // consumer rules of the library keep everything the native code needs. It is signed with the debug key so it
      // can be installed for a test.
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      isDebuggable = false
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }
}

dependencies {
  implementation(project(":jwebgpu-jni"))
}
