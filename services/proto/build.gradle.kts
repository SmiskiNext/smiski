import com.google.protobuf.gradle.id
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the


plugins {
    id("io.github.smiskinext.plugin.jvm.base")
    alias(libs.plugins.protobuf)
    alias(libs.plugins.buildBuf)
    `java-library`
}

group = "io.github.smiskinext.services"
version = "0.0.1-SNAPSHOT"

val libs = the<LibrariesForLibs>()

dependencies {
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    api(libs.protobuf.java)
    api(libs.protobuf.java.util)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.protocGenGrpcJava.get()}"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc") {
                    option("@generated=omit")
                }
            }
        }
    }
}

buf {
}

tasks.named<Jar>("jar") {
    enabled = true
}
