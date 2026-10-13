// Pin vulnerable plugin-classpath transitive dependencies before the plugins DSL resolves them.
buildscript {
    configurations.classpath {
        resolutionStrategy.force(
            "io.netty:netty-common:4.1.137.Final",
            "io.netty:netty-buffer:4.1.137.Final",
            "io.netty:netty-transport:4.1.137.Final",
            "io.netty:netty-transport-native-unix-common:4.1.137.Final",
            "io.netty:netty-resolver:4.1.137.Final",
            "io.netty:netty-codec:4.1.137.Final",
            "io.netty:netty-codec-http:4.1.137.Final",
            "io.netty:netty-codec-http2:4.1.137.Final",
            "io.netty:netty-codec-socks:4.1.137.Final",
            "io.netty:netty-handler:4.1.137.Final",
            "io.netty:netty-handler-proxy:4.1.137.Final",
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcpkix-jdk18on:1.85",
            "com.squareup.wire:wire-runtime:6.4.5",
            "com.squareup.wire:wire-runtime-jvm:6.4.5",
            "org.bitbucket.b_c:jose4j:0.9.6",
            "org.jdom:jdom2:2.0.6.1",
            "org.apache.commons:commons-lang3:3.18.0",
            "org.apache.httpcomponents:httpclient:4.5.14",
            "org.apache.httpcomponents:httpmime:4.5.14",
            "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21"
        )
    }
}

// Top-level build file. Plugin versions declared here, applied in :app.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.test") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // Baseline-profile generation (Macrobenchmark). The 1.5.x line is aligned with AGP 9.
    id("androidx.baselineprofile") version "1.5.0" apply false
}

// AGP lint and Android test-platform configurations are created by plugins, outside app runtime.
allprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "io.netty:netty-common:4.1.137.Final",
            "io.netty:netty-buffer:4.1.137.Final",
            "io.netty:netty-transport:4.1.137.Final",
            "io.netty:netty-transport-native-unix-common:4.1.137.Final",
            "io.netty:netty-resolver:4.1.137.Final",
            "io.netty:netty-codec:4.1.137.Final",
            "io.netty:netty-codec-http:4.1.137.Final",
            "io.netty:netty-codec-http2:4.1.137.Final",
            "io.netty:netty-codec-socks:4.1.137.Final",
            "io.netty:netty-handler:4.1.137.Final",
            "io.netty:netty-handler-proxy:4.1.137.Final",
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcpkix-jdk18on:1.85",
            "org.bouncycastle:bcutil-jdk18on:1.85",
            "org.apache.commons:commons-lang3:3.18.0",
            "org.apache.httpcomponents:httpclient:4.5.14",
            "org.apache.httpcomponents:httpmime:4.5.14"
        )
    }
}
