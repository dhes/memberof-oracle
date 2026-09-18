plugins {
  kotlin("jvm") version "2.3.20"
  application
}

// Pinned deliberately: the golden output records this version. Bump as a conscious act.
val hapiCoreVersion = "6.9.4.1"

dependencies {
  implementation("ca.uhn.hapi.fhir:org.hl7.fhir.r4:$hapiCoreVersion")
  implementation("ca.uhn.hapi.fhir:org.hl7.fhir.utilities:$hapiCoreVersion")
  implementation("ca.uhn.hapi.fhir:org.hl7.fhir.r5:$hapiCoreVersion")
  // HAPI core declares these as optional; versions follow org.hl7.fhir.core 6.9.4.1's POM.
  implementation("com.google.code.gson:gson:2.13.1")
  runtimeOnly("org.apache.commons:commons-compress:1.27.1")
  runtimeOnly("commons-io:commons-io:2.17.0")
  runtimeOnly("org.fhir:ucum:1.0.10")
  runtimeOnly("org.ogce:xpp3:1.1.6")
  runtimeOnly("com.squareup.okhttp3:okhttp-jvm:5.3.2")
  runtimeOnly("com.squareup.okio:okio-jvm:3.16.4")
  runtimeOnly("org.apache.commons:commons-collections4:4.4")
  runtimeOnly("org.apache.commons:commons-lang3:3.18.0")
  runtimeOnly("com.google.guava:guava:32.0.1-jre")
  runtimeOnly("com.squareup.okhttp3:logging-interceptor:5.3.2")
  // Only needed for the terminology-server run.
  runtimeOnly("org.apache.httpcomponents:httpclient:4.5.14")
  runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

kotlin { jvmToolchain(21) }

application { mainClass = "oracle.MainKt" }

tasks.named<JavaExec>("run") {
  workingDir = rootDir
  systemProperty("hapiCoreVersion", hapiCoreVersion)
}
