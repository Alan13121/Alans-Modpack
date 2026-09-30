package dev.alan.mineworld;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MineWorldTest {
    @Test void modIdIsValid() {
        assertTrue(MineWorldMod.MOD_ID.matches("[a-z][a-z0-9_]*"));
    }
}
