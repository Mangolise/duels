package net.mangolise.duels.variants;

import net.mangolise.duels.Variant;
import net.minestom.server.entity.GameMode;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;

import java.util.Map;

public class PearlFightVariant implements Variant {

    @Override
    public GameMode gameMode() {
        return GameMode.SURVIVAL;
    }

    @Override
    public Map<Integer, ItemStack> kit() {
        return Map.of(
                0, ItemStack.of(Material.STICK, 1).with(ItemComponent.ENCHANTMENTS, new EnchantmentList(Enchantment.KNOCKBACK, 2)),
                4, ItemStack.of(Material.STONE, 16),
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
        return 73;
    }
}
