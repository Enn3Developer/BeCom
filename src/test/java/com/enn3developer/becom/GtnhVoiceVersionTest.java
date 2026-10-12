package com.enn3developer.becom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;

/**
 * DESIGN.md 16.4: BeCom builds against GTNH Voice 0.10.1, and an older one fails on a missing API after loading. FML
 * checks the version only if the dependency names one, written as GTNH Voice writes its own, with the "v".
 */
class GtnhVoiceVersionTest {

    /** BeCom's GTNH Voice requirement, parsed the way FML's loader parses it. */
    private static ArtifactVersion required() {
        for (String dependency : BeCom.class.getAnnotation(Mod.class)
            .dependencies()
            .split(";")) {
            String[] parts = dependency.trim()
                .split(":");
            if (parts[0].equals("required-after") && parts[1].startsWith("gtnhvoice"))
                return VersionParser.parseVersionReference(parts[1]);
        }
        return fail("BeCom doesn't require GTNH Voice");
    }

    @ParameterizedTest
    @ValueSource(strings = { "v0.10.1", "v0.10.2", "v0.11.0", "v1.0.0" })
    void loadsWithGtnhVoice(String version) {
        assertTrue(
            required().containsVersion(new DefaultArtifactVersion("gtnhvoice", version)),
            "loads with " + version);
    }

    @ParameterizedTest
    @ValueSource(strings = { "v0.9.2", "v0.10.0" })
    void refusesAnOlderGtnhVoice(String version) {
        assertFalse(required().containsVersion(new DefaultArtifactVersion("gtnhvoice", version)), "refuses " + version);
    }
}
