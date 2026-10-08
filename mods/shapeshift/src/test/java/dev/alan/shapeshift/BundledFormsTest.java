package dev.alan.shapeshift;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Every form file shipped with the mod must parse; a typo would silently drop that form's abilities. */
class BundledFormsTest {
    @Test void allBundledFormsParse() throws Exception {
        var dir = Path.of("src/main/resources/data/minecraft/shapeshift/forms");
        int count = 0;
        try (var files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                var result = FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)));
                assertTrue(result.isSuccess(), file.getFileName() + ": " + result.error().map(Object::toString).orElse(""));
                count++;
            }
        }
        var pillager = FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(dir.resolve("pillager.json"))));
        assertTrue(pillager.getOrThrow().rare(), "pillager is a rare form");
        var zombie = FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(dir.resolve("zombie.json"))));
        assertFalse(zombie.getOrThrow().rare(), "zombie is a common form");
        assertTrue(count >= 12, "expected the bundled forms, found " + count);
    }
}
