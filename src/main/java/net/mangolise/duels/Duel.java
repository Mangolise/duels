package net.mangolise.duels;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.mangolise.combat.events.PlayerAttackEvent;
import net.mangolise.combat.events.PlayerKilledEvent;
import net.mangolise.gamesdk.BaseGame;
import net.mangolise.gamesdk.util.ChatUtil;
import net.mangolise.gamesdk.util.GameSdkUtils;
import net.mangolise.gamesdk.util.Timer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Duel extends BaseGame<Duel.Config> {
    private static final int GAME_END_WAIT_MS = 5000;
    private final CompletableFuture<Player> gameFinished = new CompletableFuture<>();
    private Instance world;
    private boolean inCombat = false;

    protected Duel(Config config) {
        super(config);
        if (config.players.size() != 2) {
            throw new IllegalArgumentException("Config must contain 2 players exactly.");
        }
    }

    @Override
    public List<Feature<?>> features() {
        return List.of();
    }

    @Override
    public void setup() {
        super.setup();

        world = MinecraftServer.getInstanceManager().createInstanceContainer(GameSdkUtils.getPolarLoaderFromResource("worlds/fruit.polar"));
        world.enableAutoChunkLoad(true);

        world.loadChunk(new Vec(0, 0, 0)).join();
        Pos pos = GameSdkUtils.getSpawnPosition(world);
        config.players.forEach(p -> {
            p.setInstance(world, pos);
            p.setRespawnPoint(pos);
            p.setGameMode(GameMode.SURVIVAL);
        });

        Timer.countDown(5, 20, time -> {
                    config.players.forEach(p -> {
                        p.playSound(Sound.sound(SoundEvent.ENTITY_WARDEN_HEARTBEAT, Sound.Source.MASTER, 1f, 1f));
                        p.showTitle(Title.title(
                                Component.text(""),
                                ChatUtil.toComponent("&cStarting in &6" + time + "&c seconds"),
                                Title.Times.times(Duration.ZERO, Duration.of(1, ChronoUnit.SECONDS), Duration.ZERO)));
                    });
                }).thenAccept(ignored -> {  // Timer done
                    config.players.forEach(p -> {
                        p.playSound(Sound.sound(SoundEvent.ENTITY_WITHER_SPAWN, Sound.Source.MASTER, 0.5f, 1f));
                    });
                    inCombat = true;
                });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerAttackEvent.class, this::onAttack);
        MinecraftServer.getGlobalEventHandler().addListener(PlayerKilledEvent.class, this::onKill);
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, e -> {
            PlayerKilledEvent death = new PlayerKilledEvent(e.getPlayer(), null, null, null, null);
            onKill(death);
        });
    }

    private void onAttack(PlayerAttackEvent e) {
        if (!e.attacker().getInstance().equals(world)) {
            return;
        }

        if (!inCombat) {
            e.setCancelled(true);
            return;
        }
    }

    private void onKill(PlayerKilledEvent e) {
        if (!e.victim().getInstance().equals(world)) {
            return;
        }

        e.victim().setGameMode(GameMode.SPECTATOR);

        Player winner = null;
        for (Player player : config().players) {
            if (player.equals(e.victim())) {
                if (!player.isOnline()) {
                    continue;
                }
                player.showTitle(Title.title(ChatUtil.toComponent("&cYou lose"), Component.text("")));
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                    player.playSound(Sound.sound(SoundEvent.ENTITY_BLAZE_DEATH, Sound.Source.MASTER, 0.3f, 1f));
                });
                continue;
            }

            // player is the killer, they won
            winner = player;
            player.showTitle(Title.title(ChatUtil.toComponent("&aYou Win!"), Component.text("")));
            player.playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 0.7f, 1f));
        }

        final Player finalWinner = winner;
        MinecraftServer.getSchedulerManager().scheduleTask(
                () -> onGameFinished().complete(finalWinner),
                TaskSchedule.millis(GAME_END_WAIT_MS),
                TaskSchedule.stop());
    }

    public CompletableFuture<Player> onGameFinished() {
        return gameFinished;
    }

    public record Config(List<Player> players) { }
}
