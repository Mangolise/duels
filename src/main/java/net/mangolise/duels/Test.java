package net.mangolise.duels;

import net.mangolise.duels.variants.PearlFightVariant;
import net.mangolise.gamesdk.limbo.Limbo;
import net.mangolise.gamesdk.log.Log;
import net.mangolise.gamesdk.util.GameSdkUtils;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.extras.bungee.BungeeCordProxy;
import net.minestom.server.permission.Permission;

import java.util.*;

public class Test {
    private static final int TEAMS = 3;
    private static final int TEAM_SIZE = 1;

    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init();
        MinecraftServer.getConnectionManager().setUuidProvider((connection, username) -> GameSdkUtils.createFakeUUID(username));

        if (GameSdkUtils.useBungeeCord()) {
            BungeeCordProxy.enable();
        }

        DuelsGame game = new DuelsGame(new DuelsGame.Config());
        game.setup();

        queueNext();

        // give every permission to every player
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, e ->
                e.getPlayer().addPermission(new Permission("*")));

        server.start("0.0.0.0", GameSdkUtils.getConfiguredPort());
    }

    private static void queueNext() {
        Log.logger().info("Waiting for players to start game");
        Limbo.waitForPlayers(TEAMS * TEAM_SIZE).thenAccept(players -> {
            final Set<Player> participants = new HashSet<>(players);
            Log.logger().info("Got players for game... Starting");
            List<Player> currentTeam = new ArrayList<>();
            List<List<Player>> teams = new ArrayList<>();
            for (Player player : participants) {
                currentTeam.add(player);
                if (currentTeam.size() == TEAM_SIZE) {
                    teams.add(currentTeam);
                    currentTeam = new ArrayList<>();
                }
            }
            Duel duel = new Duel(new Duel.Config(teams, new PearlFightVariant(), "michael"));
            duel.onGameFinished().thenAccept(winners -> participants.forEach(p -> {
                if (p != null && p.isOnline()) {
                    p.kick("The game is over! Rejoin to play again. " + (winners.contains(p) ? "Congrats on the win!" : "L noob."));
                } else {
                    Log.logger().info("Could not kick offline player: {}", p);
                }
            }));
            duel.setup();  // Start it
            Log.logger().info("Started game");
            queueNext();
        });
    }
}
