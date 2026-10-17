plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("load")
    }
}

// Times BeCom's per-tick work at a busy server's scale and prints a report. Timings depend on the
// machine, so it's run by hand and kept out of the build.
val loadTest by tasks.registering(Test::class) {
    description = "Times BeCom's surveys, access, direct links and routing against BeCom's per-tick budgets"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("load")
    }
    testLogging {
        showStandardStreams = true
    }
    outputs.upToDateWhen { false }
}
