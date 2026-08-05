plugins {
    java
    application
}

dependencies {
    implementation("com.google.code.gson:gson:2.8.5")
    testImplementation("junit:junit:4.12")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(11)
}

application {
    mainClass.set("com.itemsearch.datagen.DropDataGenerator")
}

// run from the repo root so the default input/output relative paths resolve
tasks.named<JavaExec>("run") {
    workingDir = projectDir
}
