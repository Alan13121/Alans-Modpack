package dev.alan.logistics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogisticsTest {
    @Test void modIdIsValid() {
        assertTrue(LogisticsMod.MOD_ID.matches("[a-z][a-z0-9_]*"));
    }
}
