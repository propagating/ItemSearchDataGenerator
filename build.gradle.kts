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

// second entry point: shop data generation (same repo-root working dir as "run")
tasks.register<JavaExec>("runShops") {
    mainClass.set("com.itemsearch.datagen.ShopDataGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}

// third entry point: the wiki drop sweep that produces sources.json
tasks.register<JavaExec>("runSources") {
    mainClass.set("com.itemsearch.datagen.SourceDataGenerator")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}
