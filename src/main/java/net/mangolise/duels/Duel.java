package net.mangolise.duels;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.mangolise.combat.events.PlayerAttackEvent;
import net.mangolise.combat.events.PlayerKilledEvent;
import net.mangolise.gamesdk.log.Log;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Duel {
    private static final int GAME_END_WAIT_MS = 5000;
    private final CompletableFuture<List<Player>> gameFinished = new CompletableFuture<>();
    private Instance world;
    private boolean inCombat = false;
    private List<Team> remainingTeams;
    private final Config config;

    protected Duel(Config config) {
        this.config = config;
    }

    private void forEachPlayer(Consumer<Player> consumer) {
        config.teams.forEach(t -> t.forEach(consumer));
    }

    public void setup() {
        remainingTeams = new ArrayList<>();
        config.teams.forEach(tpl -> {
            remainingTeams.add(new Team(tpl));

            if (tpl.size() > 1) {
                tpl.forEach(p -> p.sendMessage(ChatUtil.toComponent("&aYou are teamed with: &6" + String.join(", ",
                        tpl
                                .stream()
                                .filter(p2 -> !p2.equals(p))
                                .map(Player::getUsername).toList()))));
            }
        });

        world = MinecraftServer.getInstanceManager().createInstanceContainer(GameSdkUtils.getPolarLoaderFromResource("worlds/" + config.map + ".polar"));
        world.enableAutoChunkLoad(true);

        world.loadChunk(new Vec(0, 0, 0)).join();
        Pos pos = GameSdkUtils.getSpawnPosition(world);
        forEachPlayer(p -> {
            p.setInstance(world, pos);
            p.setRespawnPoint(pos);
            p.setGameMode(config.variant().gameMode());
            config.variant.kit().forEach((key, value) -> p.getInventory().setItemStack(key, value));
        });

        Timer.countDown(5, 20, time -> {
                    forEachPlayer(p -> {
                        p.playSound(Sound.sound(SoundEvent.ENTITY_WARDEN_HEARTBEAT, Sound.Source.MASTER, 1f, 1f));
                        p.showTitle(Title.title(
                                Component.text(""),
                                ChatUtil.toComponent("&cStarting in &6" + time + "&c seconds"),
                                Title.Times.times(Duration.ZERO, Duration.of(1, ChronoUnit.SECONDS), Duration.ZERO)));
                    });
                }).thenAccept(ignored -> {  // Timer done
                    forEachPlayer(p -> p.playSound(Sound.sound(SoundEvent.ENTITY_WITHER_SPAWN, Sound.Source.MASTER, 0.5f, 1f)));
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
        }

        if (config.teams.stream().anyMatch(team -> team.contains(e.attacker()) && team.contains(e.victim()))) {
            e.setCancelled(true);
        }

        if (!config.variant.attackingEnabled()) {
            e.setCancelled(true);
        }

        if (!config.variant.damageEnabled()) {
            e.setDamage(0);
        }
    }

    private void onKill(PlayerKilledEvent e) {
        if (!e.victim().getInstance().equals(world)) {
            return;
        }

        e.victim().setGameMode(GameMode.SPECTATOR);
        Component killerName = e.killer() == null ? ChatUtil.toComponent("&7poor decision making") : ChatUtil.getDisplayName(Objects.requireNonNull(e.killer()));
        e.setDeathMsg(ChatUtil.getDisplayName(e.victim()).append(ChatUtil.toComponent(" &7was killed by ").append(killerName)));

        for (Team team : remainingTeams) {
            if (!team.removeAlive(e.victim())) {
                continue;
            }

            // Team is eliminated
            team.members().forEach(p -> p.sendMessage(ChatUtil.toComponent("&cYour team has been eliminated!")));
            remainingTeams.remove(team);
            if (remainingTeams.size() == 1) {
                gameEnd();
            }
            break;
        }

        Log.logger().info("Remaining teams: {}", remainingTeams.size());
    }

    private void gameEnd() {
        if (remainingTeams.size() != 1) {
            throw new IllegalStateException("There isn't one team left, remove all dead teams first");
        }

        Team winningTeam = remainingTeams.getFirst();

        forEachPlayer(player -> {
            if (winningTeam.members().contains(player)) {  // dub
                if (!player.isOnline()) {
                    return;
                }
                player.showTitle(Title.title(ChatUtil.toComponent("&aYou Win!"), Component.text("")));
                player.playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 0.7f, 1f));
                return;
            }
            // L

            if (!player.isOnline()) {
                return;
            }
            player.showTitle(Title.title(ChatUtil.toComponent("&cYou lose"), Component.text("")));
            MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                player.playSound(Sound.sound(SoundEvent.ENTITY_BLAZE_DEATH, Sound.Source.MASTER, 0.3f, 1f));
            });
        });

        MinecraftServer.getSchedulerManager().scheduleTask(
                () -> onGameFinished().complete(winningTeam.members()),
                TaskSchedule.millis(GAME_END_WAIT_MS),
                TaskSchedule.stop());
    }

    public CompletableFuture<List<Player>> onGameFinished() {
        return gameFinished;
    }

    public record Config(List<List<Player>> teams, Variant variant, String map) { }
}
