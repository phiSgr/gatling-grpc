import com.google.protobuf.gradle.*
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem

plugins {
    id("com.google.protobuf") version "0.8.14"

    id("io.gatling.gradle") version "3.8.3.2"
    id("scala")
    idea
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.github.phisgr:gatling-grpc:0.15.1")
    implementation("io.gatling:gatling-core-java:3.8.4")
    implementation("com.github.phisgr:gatling-javapb:1.3.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}

val scalapbVersion = "0.11.11"

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.14.0"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.34.1"
        }
        id("scalapb") {
            artifact = if (getCurrentOperatingSystem().isWindows) {
                "com.thesamet.scalapb:protoc-gen-scala:${scalapbVersion}:windows@bat"
            } else {
                "com.thesamet.scalapb:protoc-gen-scala:${scalapbVersion}:unix@sh"
            }
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc")
                id("scalapb") {
                    option("grpc")
                }
            }
        }
    }
}

sourceSets {
    main {
        scala {
            srcDirs("${protobuf.protobuf.generatedFilesBaseDir}/main/scalapb")
        }
    }
}
