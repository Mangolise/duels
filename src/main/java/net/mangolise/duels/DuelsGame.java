package net.mangolise.duels;

import net.mangolise.combat.CombatConfig;
import net.mangolise.combat.MangoCombat;
import net.mangolise.gamesdk.BaseGame;
import net.mangolise.gamesdk.features.*;

import java.util.List;

public class DuelsGame extends BaseGame<DuelsGame.Config> {

    protected DuelsGame(Config config) {
        super(config);
    }

    @Override
    public List<Feature<?>> features() {
        return List.of(
                new AdminCommandsFeature(),
                new FireFeature(),
                new LavaHurtFeature(),
                new LiquidFeature(),
                new ItemDropFeature(),
                new ItemPickupFeature(),
                new EnderPearlFeature()
        );
    }

    @Override
    public void setup() {
        super.setup();

        MangoCombat.enableGlobal(CombatConfig.create().withFakeDeath(true).withVoidDeath(true));
    }

    public record Config() {

    }
}
