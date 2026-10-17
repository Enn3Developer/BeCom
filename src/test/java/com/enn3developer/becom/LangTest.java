package com.enn3developer.becom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.net.LinkRules;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.becom.voice.RadioGroup;
import com.enn3developer.becom.voice.RadioRouting;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;

/**
 * Every player-facing string is in the language files. The keys built from an enum's names are the
 * ones a new constant can leave out, so each of those enums is checked whole, and Italian must follow English key for
 * key.
 */
class LangTest {

    private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]");

    private static Map<String, String> entries(String file) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (InputStream in = LangTest.class.getResourceAsStream("/assets/becom/lang/" + file)) {
            assertNotNull(in, file + " on the classpath");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                int equals = line.indexOf('=');
                if (equals > 0 && !line.startsWith("#"))
                    entries.put(line.substring(0, equals), line.substring(equals + 1));
            }
        }
        return entries;
    }

    private static Set<String> keys() throws IOException {
        return entries("en_US.lang").keySet();
    }

    private static List<String> formats(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = FORMAT.matcher(text);
        while (matcher.find()) found.add(matcher.group());
        found.sort(null);
        return found;
    }

    @Test
    void italianHasEveryEnglishKeyWithTheSameFormats() throws IOException {
        Map<String, String> english = entries("en_US.lang");
        Map<String, String> italian = entries("it_IT.lang");
        List<String> missing = new ArrayList<>();
        List<String> wrongFormats = new ArrayList<>();
        for (Map.Entry<String, String> entry : english.entrySet()) {
            String text = italian.get(entry.getKey());
            if (text == null) missing.add(entry.getKey());
            else if (!formats(text).equals(formats(entry.getValue()))) wrongFormats.add(entry.getKey());
        }
        List<String> extra = new ArrayList<>(italian.keySet());
        extra.removeAll(english.keySet());
        assertEquals(List.of(), missing, "keys missing from it_IT.lang");
        assertEquals(List.of(), extra, "keys only in it_IT.lang");
        assertEquals(List.of(), wrongFormats, "format specifiers that differ from English");
    }

    private static String lower(Enum<?> constant) {
        return constant.name()
            .toLowerCase(Locale.ROOT);
    }

    @Test
    void everyKeyBuiltFromAnEnumIsThere() throws IOException {
        List<String> wanted = new ArrayList<>();
        for (QuestTask task : QuestTask.values()) wanted.add("becom.task." + lower(task));
        for (SignalLevel level : SignalLevel.values()) wanted.add("becom.level." + lower(level));
        for (RadioModel.Loss loss : RadioModel.Loss.values()) wanted.add("becom.loss." + lower(loss));
        for (PhoneRecord.Direction direction : PhoneRecord.Direction.values()) {
            wanted.add("becom.phone.recent." + lower(direction));
        }
        for (LinkRules.Result result : LinkRules.Result.values()) {
            if (result != LinkRules.Result.OK) wanted.add("becom.link.refused." + lower(result));
        }
        for (NodeKind kind : List.of(NodeKind.PYLON, NodeKind.EXCHANGE)) {
            wanted.add("becom.machine." + lower(kind) + ".type");
            wanted.add("becom.machine." + lower(kind) + ".info");
        }
        Set<String> keys = keys();
        List<String> missing = new ArrayList<>();
        for (String key : wanted) if (!keys.contains(key)) missing.add(key);
        assertEquals(List.of(), missing);
    }

    /**
     * What a client shows for a voice group in {@code language}: GTNH Voice sends the group's display component as
     * JSON, and the client formats it with its own lang files.
     */
    private static String clientText(IGroup group, Map<String, String> language) {
        String json = IChatComponent.Serializer.func_150696_a(group.getDisplayComponent());
        ChatComponentTranslation sent = (ChatComponentTranslation) IChatComponent.Serializer.func_150699_a(json);
        return String.format(language.get(sent.getKey()), sent.getFormatArgs());
    }

    @Test
    void eachClientShowsVoiceGroupNamesInItsOwnLanguage() throws IOException {
        IGroup call = new CallGroup(16, () -> 48, () -> 0);
        IGroup radio = new RadioGroup(3, new RadioRouting(16, () -> 48, 6, 16, 1, 3, () -> 0, () -> 0));
        Map<String, String> english = entries("en_US.lang");
        Map<String, String> italian = entries("it_IT.lang");
        assertEquals("Call", clientText(call, english));
        assertEquals("Chiamata", clientText(call, italian));
        assertEquals("Radio 3", clientText(radio, english));
        assertEquals("Radio 3", clientText(radio, italian));
        // Logs, and a client without BeCom's lang files, get the names in English.
        assertEquals("Call", call.getDisplayName());
        assertEquals("Radio 3", radio.getDisplayName());
    }
}
