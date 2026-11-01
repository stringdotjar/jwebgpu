// Shared Java setup (toolchain, Checkstyle, Javadoc rules) for modules that are never published, such as
// the build-time generator. Published modules apply jwebgpu.java-library, which builds on this.
plugins {
  id("jwebgpu.java-base")
  `java-library`
  checkstyle
}

checkstyle {
  toolVersion = "10.21.0"
  configDirectory.set(rootProject.layout.projectDirectory.dir("gradle/checkstyle"))
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

tasks.withType<Javadoc>().configureEach {
  options.encoding = "UTF-8"
  (options as StandardJavadocDocletOptions).apply {
    charSet = "UTF-8"
    docEncoding = "UTF-8"
    memberLevel = JavadocMemberLevel.PUBLIC
    links("https://docs.oracle.com/en/java/javase/25/docs/api/")
    if (JavaVersion.current().isJava9Compatible) {
      addStringOption("Xdoclint:all,-missing", "-quiet")
      addStringOption("Werror")
    }
  }
  isFailOnError = true
}
