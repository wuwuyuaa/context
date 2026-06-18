plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.threadmap"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // 插件运行时只需 core 的数据模型 + json reader(只用 jackson);
    // 排除引擎侧重依赖(标注/采集用,插件用不到),避免插件臃肿与无版本传递依赖解析失败。
    // 后续(发布前)考虑拆出轻量 threadmap-model 模块替代此 exclude 方案。
    implementation(project(":threadmap-core")) {
        exclude(group = "dev.langchain4j")
        exclude(group = "com.github.javaparser")
        exclude(group = "org.springframework")
        exclude(group = "org.aspectj")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.4")
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("com.intellij.java")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
    }
}

tasks.named("instrumentCode") {
    enabled = false
}

tasks.named("instrumentTestCode") {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}
