package net.mangolise.duels;

import net.mangolise.combat.CombatConfig;
import net.mangolise.combat.MangoCombat;
import net.mangolise.gamesdk.limbo.Limbo;
import net.mangolise.gamesdk.log.Log;
import net.mangolise.gamesdk.util.GameSdkUtils;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.extras.bungee.BungeeCordProxy;
import net.minestom.server.permission.Permission;

import java.util.List;

public class Test {

    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init();
        MinecraftServer.getConnectionManager().setUuidProvider((connection, username) -> GameSdkUtils.createFakeUUID(username));

        if (GameSdkUtils.useBungeeCord()) {
            BungeeCordProxy.enable();
        }

        MangoCombat.enableGlobal(CombatConfig.create().withFakeDeath(true));

        queueNext();

        // give every permission to every player
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, e ->
                e.getPlayer().addPermission(new Permission("*")));

        server.start("0.0.0.0", GameSdkUtils.getConfiguredPort());
    }

    private static void queueNext() {
        Limbo.waitForPlayers(2).thenAccept(players -> {
            final List<Player> participants = players.stream().toList();
            Duel duel = new Duel(new Duel.Config(participants));
            duel.onGameFinished().thenAccept(winner -> {
                participants.forEach(p -> {
                    if (p != null && p.isOnline()) {
                        p.kick("The game is over! Rejoin to play again. " + (p.equals(winner) ? "Congrats on the win!" : "L noob."));
                    } else {
                        Log.logger().info("Could not kick offline player: {}", p);
                    }
                });
            });
            duel.setup();  // Start it
            queueNext();
        });
    }
}
