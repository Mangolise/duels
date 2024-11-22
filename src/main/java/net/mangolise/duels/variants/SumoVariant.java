package net.mangolise.duels.variants;

import net.mangolise.duels.Variant;
import net.minestom.server.entity.GameMode;
import net.minestom.server.item.ItemStack;

import java.util.Map;

public class SumoVariant implements Variant {

    @Override
    public GameMode gameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    public Map<Integer, ItemStack> kit() {
        return Map.of();
    }

    @Override
    public boolean damageEnabled() {
        return false;
    }

    @Override
    public boolean attackingEnabled() {
        return true;
    }

    @Override
    public boolean allowArenaBreak() {
        return false;
    }

    @Override
    public int blockDisappearTime() {
        return 7;
    }

    @Override
    public int arenaHeight() {
        return 255;
    }
}
