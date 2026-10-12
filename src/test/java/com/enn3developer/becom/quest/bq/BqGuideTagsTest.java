package com.enn3developer.becom.quest.bq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Without GuideNH, a quest's link to its guide page shows its label alone. */
class BqGuideTagsTest {

    @Test
    void aGuideLinkLeavesItsLabel() {
        assertEquals(
            "Up 32 blocks. More in the guide: The wired network.",
            BqGuideTags
                .strip("Up 32 blocks. More in the guide: [guide page=becom:wired.md]The wired network[/guide]."));
    }

    @Test
    void textWithoutLinksStaysAsItIs() {
        assertEquals("[guide] alone", BqGuideTags.strip("[guide] alone"));
        assertEquals("plain", BqGuideTags.strip("plain"));
        assertNull(BqGuideTags.strip(null));
    }
}
