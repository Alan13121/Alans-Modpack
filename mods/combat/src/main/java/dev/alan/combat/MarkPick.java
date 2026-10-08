package dev.alan.combat;

import java.util.List;

/** Which locked rare form a form mark points at; pure so it can be unit tested. */
final class MarkPick {
    private MarkPick() {}

    /**
     * The form to act on: the current target while it is still locked, otherwise the first locked one. When
     * {@code next} is set, the one after the current target (wrapping around).
     */
    static String pick(List<String> locked, String current, boolean next) {
        int index = current == null ? -1 : locked.indexOf(current);
        if (next) return locked.get((index + 1) % locked.size());
        return index >= 0 ? locked.get(index) : locked.get(0);
    }
}
