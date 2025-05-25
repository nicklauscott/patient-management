plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("software.amazon.awscdk:aws-cdk-lib:2.178.1")
    implementation("com.amazonaws:aws-java-sdk:1.12.780")
}

tasks.test {
    useJUnitPlatform()
}