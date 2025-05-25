// this should be available after sync the dependencies
import com.google.protobuf.gradle.id

plugins {
	java
	id("org.springframework.boot") version "3.4.5"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.protobuf") version "0.9.4"
}

group = "com.pm"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// gRPC
	implementation("io.grpc:grpc-netty-shaded:1.69.0")
	implementation("io.grpc:grpc-protobuf:1.69.0")
	implementation("io.grpc:grpc-stub:1.69.0")

	// For Java 9+ compatibility
	compileOnly("org.apache.tomcat:annotations-api:6.0.53")

	// Protobuf
	implementation("com.google.protobuf:protobuf-java:4.29.1")

	// Spring Boot gRPC Integration
	implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")


	implementation("org.springframework.boot:spring-boot-starter-web")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// this should be added after sync the dependencies
protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:3.25.5"
	}
	plugins {
		id("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:1.68.1"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
			task.plugins {
				id("grpc")
			}
		}
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
