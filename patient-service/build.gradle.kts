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

	// kafka
	implementation("org.springframework.kafka:spring-kafka:3.3.0")

	// gRPC core dependencies
	implementation("io.grpc:grpc-netty-shaded:1.69.0")
	implementation("io.grpc:grpc-protobuf:1.69.0")
	implementation("io.grpc:grpc-stub:1.69.0")
	implementation("com.google.protobuf:protobuf-java:4.29.1")

	// gRPC Spring Boot integration
	implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")

	// Java 9+ annotations workaround
	compileOnly("org.apache.tomcat:annotations-api:6.0.53")

	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
	implementation("com.h2database:h2")

	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

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
		all().forEach {
			it.plugins {
				id("grpc")
			}
		}
	}
}

tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
	options.release.set(21)
}


tasks.withType<Test> {
	useJUnitPlatform()
}
