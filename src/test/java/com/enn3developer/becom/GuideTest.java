package com.enn3developer.becom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.enn3developer.becom.machine.BeComMachines;

/**
 * The guide's pages, in English and Italian, and each quest pointing to its page. A quest's
 * [guide] link must name a page of its own language under that page's title, and the page must list the quest back,
 * which is how GuideNH opens it from the quest book.
 */
class GuideTest {

    private static final Pattern QUEST_DESC = Pattern
        .compile("^betterquesting\\.quest\\.([A-Za-z0-9_-]+)\\.desc=(.*)$");
    private static final Pattern GUIDE_LINK = Pattern.compile("\\[guide page=becom:([^\\] ]+)](.*?)\\[/guide]");
    private static final Pattern PAGE_LINK = Pattern.compile("]\\(([^):#]+\\.md)\\)");
    private static final Pattern TAG = Pattern.compile("<(ItemLink|ItemImage|KeyBind)\\b");
    private static final Pattern TAG_WITH_WORDS = Pattern
        .compile("<(ItemLink|ItemImage|KeyBind) id=\"([^\"]+)\"(?: [^>]*[^/>])?>([^<]+)</\\1>");

    private record Page(String title, Set<String> quests, String text) {}

    private static Path folder(String language) throws URISyntaxException {
        URL url = GuideTest.class.getResource("/assets/becom/guidenh/_" + language);
        assertNotNull(url, language + " guide on the classpath");
        return Path.of(url.toURI());
    }

    private static Map<String, Page> pages(String language) throws IOException, URISyntaxException {
        Map<String, Page> pages = new HashMap<>();
        try (Stream<Path> files = Files.list(folder(language))) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!file.toString()
                    .endsWith(".md")) continue;
                pages.put(
                    file.getFileName()
                        .toString(),
                    page(Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        return pages;
    }

    /** The frontmatter's navigation title and quest_ids, read line by line: the pages keep to a plain layout. */
    private static Page page(String text) {
        assertTrue(text.startsWith("---\n"), "frontmatter first");
        String front = text.substring(4, text.indexOf("\n---\n", 4));
        String title = null;
        Set<String> quests = new HashSet<>();
        boolean inQuests = false;
        for (String line : front.split("\n")) {
            if (line.startsWith("  title: ")) title = line.substring("  title: ".length());
            if (!line.startsWith(" ")) inQuests = line.equals("quest_ids:");
            else if (inQuests && line.startsWith("  - ")) quests.add(
                line.substring(4)
                    .replace("\"", ""));
        }
        return new Page(title, quests, text);
    }

    /** Every key and its text, from one language file. */
    private static Map<String, String> lang(String file) throws IOException {
        Map<String, String> lang = new HashMap<>();
        try (var in = GuideTest.class.getResourceAsStream("/assets/becom/lang/" + file)) {
            assertNotNull(in, file);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                int equals = line.indexOf('=');
                if (equals > 0 && !line.startsWith("#"))
                    lang.put(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return lang;
    }

    /** Quest id to its description, from one language file. */
    private static Map<String, String> questDescriptions(String file) throws IOException {
        Map<String, String> descriptions = new HashMap<>();
        for (Map.Entry<String, String> entry : lang(file).entrySet()) {
            Matcher matcher = QUEST_DESC.matcher(entry.getKey() + "=" + entry.getValue());
            if (matcher.matches()) descriptions.put(matcher.group(1), matcher.group(2));
        }
        return descriptions;
    }

    private static String langFile(String language) {
        return language.equals("en_us") ? "en_US.lang" : "it_IT.lang";
    }

    /** The language key of an item's name, for a BeCom item, block or machine id as the guide writes it. */
    private static String nameKey(String id, Map<String, String> lang) {
        String machines = "gregtech:gt.blockmachines:";
        if (id.startsWith(machines)) return "gt.blockmachines." + BeComMachines.ids()
            .get(Integer.parseInt(id.substring(machines.length()))) + ".name";
        String name = id.substring("becom:".length());
        // A tiered item names its tier by its damage: phone_cable:1 is Coaxial, switching_rack:2 the HV rack.
        int colon = name.indexOf(':');
        if (colon >= 0 && name.startsWith("becom_card:")) return "item.becom."
            + com.enn3developer.becom.item.ItemBecomCard.nameOf(Integer.parseInt(name.substring(colon + 1)))
            + ".name";
        if (colon >= 0) {
            com.enn3developer.becom.logic.net.CableTier tier = com.enn3developer.becom.logic.net.CableTier
                .byOrdinal(Integer.parseInt(name.substring(colon + 1)));
            String base = name.substring(0, colon);
            if (base.equals("phone_cable"))
                return "tile.becom." + com.enn3developer.becom.block.BlockPhoneCable.nameOf(tier) + ".name";
            return "item.becom." + base
                + "_"
                + tier.voltage()
                    .toLowerCase(java.util.Locale.ROOT)
                + ".name";
        }
        return lang.containsKey("tile.becom." + name + ".name") ? "tile.becom." + name + ".name"
            : "item.becom." + name + ".name";
    }

    @ParameterizedTest
    @ValueSource(strings = { "en_us", "it_it" })
    void everyPageHasATitleAndItsLinksLeadToPages(String language) throws Exception {
        Map<String, Page> pages = pages(language);
        assertTrue(pages.containsKey("index.md"), "a start page");
        for (Map.Entry<String, Page> entry : pages.entrySet()) {
            assertNotNull(
                entry.getValue()
                    .title(),
                entry.getKey() + " has a navigation title");
            Matcher links = PAGE_LINK.matcher(
                entry.getValue()
                    .text());
            while (links.find())
                assertTrue(pages.containsKey(links.group(1)), entry.getKey() + " links to " + links.group(1));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "en_us", "it_it" })
    void everyQuestLinksToAPageThatListsItBack(String language) throws Exception {
        Map<String, Page> pages = pages(language);
        Map<String, String> descriptions = questDescriptions(langFile(language));
        assertEquals(28, descriptions.size(), "every quest");
        Map<String, Set<String>> linked = new HashMap<>();
        for (Map.Entry<String, String> quest : descriptions.entrySet()) {
            Matcher link = GUIDE_LINK.matcher(quest.getValue());
            assertTrue(link.find(), quest.getKey() + " points to its guide page");
            Page page = pages.get(link.group(1));
            assertNotNull(page, quest.getKey() + " points to " + link.group(1));
            assertEquals(page.title(), link.group(2), quest.getKey() + "'s link is titled like its page");
            assertTrue(
                page.quests()
                    .contains(quest.getKey()),
                link.group(1) + " lists " + quest.getKey());
            linked.computeIfAbsent(link.group(1), k -> new HashSet<>())
                .add(quest.getKey());
        }
        for (Map.Entry<String, Page> page : pages.entrySet()) {
            assertEquals(
                linked.getOrDefault(page.getKey(), Set.of()),
                page.getValue()
                    .quests(),
                page.getKey() + " lists exactly the quests that point to it");
        }
    }

    /**
     * GitHub drops GuideNH's tags and keeps the words inside them, so a tag without words leaves a hole in the page
     * there. GuideNH draws an item link's words in place of the item's name, so they must be its name.
     */
    @ParameterizedTest
    @ValueSource(strings = { "en_us", "it_it" })
    void everyTagHoldsWordsAndAnItemsWordsAreItsName(String language) throws Exception {
        Map<String, String> lang = lang(langFile(language));
        for (Map.Entry<String, Page> entry : pages(language).entrySet()) {
            String text = entry.getValue()
                .text();
            int tags = 0, withWords = 0;
            for (Matcher tag = TAG.matcher(text); tag.find();) tags++;
            for (Matcher tag = TAG_WITH_WORDS.matcher(text); tag.find();) {
                withWords++;
                if (tag.group(1)
                    .equals("KeyBind")) continue;
                assertEquals(
                    lang.get(nameKey(tag.group(2), lang)),
                    tag.group(3),
                    entry.getKey() + ": the words of " + tag.group(2));
            }
            assertEquals(tags, withWords, entry.getKey() + ": tags with words inside");
            assertFalse(text.contains("/>"), entry.getKey() + " has a tag closed on itself");
        }
    }

    @org.junit.jupiter.api.Test
    void italianHasTheSamePagesListingTheSameQuests() throws Exception {
        Map<String, Page> english = pages("en_us"), italian = pages("it_it");
        assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(italian.keySet()));
        List<String> differing = new ArrayList<>();
        for (String name : english.keySet()) {
            if (!english.get(name)
                .quests()
                .equals(
                    italian.get(name)
                        .quests()))
                differing.add(name);
        }
        assertEquals(List.of(), differing, "pages whose quest_ids differ between the languages");
    }
}
