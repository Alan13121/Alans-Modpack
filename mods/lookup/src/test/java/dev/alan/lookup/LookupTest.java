package dev.alan.lookup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LookupTest {
    @Test void modIdIsValid() {
        assertTrue(LookupMod.MOD_ID.matches("[a-z][a-z0-9_]*"));
    }
}
