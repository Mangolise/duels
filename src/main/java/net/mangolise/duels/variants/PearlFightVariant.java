package net.mangolise.duels.variants;

import net.mangolise.duels.Variant;
import net.minestom.server.entity.GameMode;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Map;

public class PearlFightVariant implements Variant {

    @Override
    public GameMode gameMode() {
        return GameMode.SURVIVAL;
    }

    @Override
    public Map<Integer, ItemStack> kit() {
        return Map.of(
                0, ItemStack.of(Material.STICK, 1),
                5, ItemStack.of(Material.STONE, 16),
                8, ItemStack.of(Material.ENDER_PEARL, 8)
        );
    }

    @Override
    public boolean damageEnabled() {
        return false;
    }

    @Override
    public boolean attackingEnabled() {
        return true;
    }
}
