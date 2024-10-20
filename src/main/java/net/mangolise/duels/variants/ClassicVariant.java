package net.mangolise.duels.variants;

import net.mangolise.duels.Variant;
import net.minestom.server.entity.GameMode;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Map;

public class ClassicVariant implements Variant {

    @Override
    public GameMode gameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    public Map<Integer, ItemStack> kit() {
        return Map.of(
                0, ItemStack.of(Material.IRON_SWORD),
                41, ItemStack.of(Material.IRON_HELMET),
                42, ItemStack.of(Material.IRON_CHESTPLATE),
                43, ItemStack.of(Material.IRON_LEGGINGS),
                44, ItemStack.of(Material.IRON_BOOTS)
        );
    }

    @Override
    public boolean damageEnabled() {
        return true;
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
        return -1;
    }

    @Override
    public int arenaHeight() {
        return 255;
    }
}
