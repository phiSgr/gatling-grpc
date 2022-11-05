import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    id("org.jetbrains.dokka") version "1.7.10"

    id("io.gatling.gradle") version "3.8.3.2"
    id("me.champeau.jmh") version "0.6.6"

    id("maven-publish")
    id("signing")
    idea
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    fun add(s: String) {
        api(s)
        // weird, was expecting gatling transitively depends on main's dependencies
        gatlingImplementation(s)
    }

    add("com.github.phisgr:gatling-grpc:0.15.1-SNAPSHOT")
    add("io.gatling:gatling-core-java:3.8.4")
    add("com.github.phisgr:gatling-kt-ext:0.4.0")

    gatlingImplementation(project(":scala-tests"))
    jmh(project(":scala-tests"))
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xlambdas=indy"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

val javadocJar = tasks.named<Jar>("javadocJar") {
    from(tasks.named("dokkaHtml"))
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = properties["ossrhUsername"] as String?
                password = properties["ossrhPassword"] as String?
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.phisgr"
            artifactId = "gatling-grpc-kt"
            version = "0.15.1-SNAPSHOT"

            from(components["java"])

            pom {
                name.set("gatling-grpc-kt")
                description.set("Kotlin/Java binding for Gatling-gRPC")
                url.set("https://github.com/phiSgr/gatling-grpc/tree/master/gatling-grpc-kt")

                licenses {
                    license {
                        name.set("APL2")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("phiSgr")
                        name.set("George Leung")
                        email.set("phisgr@gmail.com")
                    }
                }
                scm {
                    url.set("https://github.com/phiSgr/gatling-grpc/tree/master/gatling-grpc-kt")
                }
            }
        }
    }
}

signing {
    sign(publishing.publications)
}

jmh {
    iterations.set(3)
    warmupIterations.set(3)
    fork.set(10)
    threads.set(1)
    profilers.set(listOf("gc"))
}
