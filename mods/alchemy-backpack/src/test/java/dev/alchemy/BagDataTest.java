package dev.alchemy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import com.mojang.serialization.JsonOps;

class BagDataTest {
    @Test void depositLearnsAndAddsStackValue() {
        var state = BagData.EMPTY.deposit("minecraft:diamond", 8192, 3);
        assertEquals(24576, state.energy());
        assertEquals(List.of("minecraft:diamond"), state.learned());
        var second = state.deposit("minecraft:diamond", 8192, 1);
        assertEquals(32768, second.energy());
        assertEquals(1, second.learned().size());
        assertEquals(0, BagData.EMPTY.energy());
    }
    @Test void exchangeConservesEnergyAndRetainsKnowledge() {
        var state = BagData.EMPTY.deposit("minecraft:stone", 1, 64).withdraw(1, 64);
        assertEquals(0, state.energy());
        assertEquals(List.of("minecraft:stone"), state.learned());
    }
    @Test void insufficientFundsNeverGoNegative() {
        var state = new BagData(63, List.of("minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> state.withdraw(1, 64));
        assertEquals(63, state.energy());
    }
    @Test void rejectsInvalidQuantitiesAndValues() {
        assertThrows(IllegalArgumentException.class, () -> BagData.EMPTY.deposit("a", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> BagData.EMPTY.deposit("a", 1, -1));
        assertThrows(IllegalArgumentException.class, () -> BagData.EMPTY.withdraw(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BagData(-1, List.of()));
    }
    @Test void overflowDoesNotMutateExistingBalance() {
        var state = new BagData(Long.MAX_VALUE, List.of());
        assertThrows(ArithmeticException.class, () -> state.deposit("a", 1, 1));
        assertEquals(Long.MAX_VALUE, state.energy());
        assertThrows(ArithmeticException.class, () -> state.withdraw(Long.MAX_VALUE, 2));
    }
    @Test void savedDataRoundTripsWithoutLosingLongPrecision() {
        var state = new BagData(9_007_199_254_740_993L, List.of("minecraft:diamond", "minecraft:stone"));
        var json = BagData.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state, BagData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
    @Test void independentBagsAndImmutableKnowledge() {
        var original = new ArrayList<>(List.of("minecraft:stone"));
        var a = new BagData(100, original);
        var b = BagData.EMPTY.deposit("minecraft:dirt", 1, 1);
        original.clear();
        assertEquals(1, a.learned().size());
        assertEquals(100, a.energy());
        assertEquals(1, b.energy());
        assertThrows(UnsupportedOperationException.class, () -> a.learned().clear());
    }
}
