package dev.alan.guide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideTest {
    @Test void modIdIsValid() {
        assertTrue(GuideMod.MOD_ID.matches("[a-z][a-z0-9_]*"));
    }
}
