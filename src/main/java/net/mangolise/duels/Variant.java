package net.mangolise.duels;

import net.minestom.server.entity.GameMode;
import net.minestom.server.item.ItemStack;

import java.util.Map;

public interface Variant {
    GameMode gameMode();
    Map<Integer, ItemStack> kit();
    boolean damageEnabled();
    boolean attackingEnabled();
    boolean allowArenaBreak();
    int blockDisappearTime();  // ms
    int arenaHeight();
}
