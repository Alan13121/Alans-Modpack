package dev.alan.guide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideTest {
    @Test void modIdIsValid() {
        assertTrue(GuideMod.MOD_ID.matches("[a-z][a-z0-9_]*"));
    }

    @Test void everyHandbookEntryHasTextInBothLanguages() throws Exception {
        int entries;
        try (var in = GuideTest.class.getResourceAsStream("/assets/guide/handbook.json")) {
            entries = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray().size();
        }
        assertTrue(entries > 0);
        for (String lang : new String[] {"en_us", "zh_tw"}) {
            try (var in = GuideTest.class.getResourceAsStream("/assets/guide/lang/" + lang + ".json")) {
                var json = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (int i = 1; i <= entries; i++) {
                    assertTrue(json.has("handbook.guide.entry." + i + ".title"), lang + " title " + i);
                    assertTrue(json.has("handbook.guide.entry." + i + ".body"), lang + " body " + i);
                }
                assertTrue(json.has("item.guide.handbook"));
            }
        }
    }
}
