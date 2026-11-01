plugins {
  id("com.diffplug.spotless")
}

spotless {
  java {
    target("jwebgpu-jni/src/**/*.java")
    jwebgpuRules(rootDir)
  }
}
