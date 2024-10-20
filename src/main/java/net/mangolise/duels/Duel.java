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
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class Duel {
    private static final int GAME_END_WAIT_MS = 5000;
    private static final int OUT_OF_BOUNDS_TIME = 5;
    private static final Tag<Long> LAST_IN_BOUNDS_TAG = Tag.Long("duels_last_in_bounds");
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
        world.eventNode().addListener(PlayerBlockBreakEvent.class, this::onBlockBreak);
        world.eventNode().addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);
        world.eventNode().addListener(PlayerMoveEvent.class, this::onMove);
        world.eventNode().addListener(InstanceTickEvent.class, this::onTick);
    }

    private void onTick(@NotNull InstanceTickEvent event) {
        if (event.getInstance().getWorldAge() % 20 != 0) {
            return;
        }

        for (Player player : event.getInstance().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            if (player.getPosition().y() <= config.variant.arenaHeight()) continue;

            player.showTitle(Title.title(
                    ChatUtil.toComponent("&cGo back to the arena"),
                    ChatUtil.toComponent("&6You will begin taking damage"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))));

            if (!player.hasTag(LAST_IN_BOUNDS_TAG)) continue;
            if (System.currentTimeMillis() - player.getTag(LAST_IN_BOUNDS_TAG) > OUT_OF_BOUNDS_TIME * 1000) {
                player.damage(new Damage(DamageType.OUT_OF_WORLD, null, null, null, 2f));
            }
        }
    }

    private void onMove(@NotNull PlayerMoveEvent event) {
        if (event.getPlayer().getPosition().y() <= config.variant.arenaHeight()) {
            event.getPlayer().setTag(LAST_IN_BOUNDS_TAG, System.currentTimeMillis());
        }
    }

    private void onBlockPlace(@NotNull PlayerBlockPlaceEvent event) {
        if (config.variant.blockDisappearTime() == -1) {
            return;
        }

        final int breakerId = ThreadLocalRandom.current().nextInt(400, 9999);
        final BlockVec pos = event.getBlockPosition();
        final CompletableFuture<Void> cancel = Timer.countDown(config.variant.blockDisappearTime(), 20, i -> {
            int stage = 9 - (int) (((double) i / config.variant.blockDisappearTime()) * 9.0);
            BlockBreakAnimationPacket breakPacket = new BlockBreakAnimationPacket(breakerId, event.getBlockPosition(), (byte) stage);
            world.sendGroupedPacket(breakPacket);
        }).thenAccept(v -> {
            event.getInstance().setBlock(pos, Block.AIR);
            event.getInstance().playSound(Sound.sound(SoundEvent.BLOCK_STONE_BREAK, Sound.Source.BLOCK, 0.4f, 1f));
            BlockBreakAnimationPacket breakPacket = new BlockBreakAnimationPacket(breakerId, event.getBlockPosition(), (byte) 0);
            world.sendGroupedPacket(breakPacket);
            event.getPlayer().getInventory().addItemStack(ItemStack.of(Objects.requireNonNull(event.getBlock().registry().material())));
        });

        AtomicReference<EventListener<PlayerBlockBreakEvent>> breakListen = new AtomicReference<>(null);

        final BlockVec blockPos = event.getBlockPosition();
        breakListen.set(EventListener.of(PlayerBlockBreakEvent.class, e -> {
            if (!blockPos.sameBlock(e.getBlockPosition())) {
                return;
            }

            cancel.complete(null);
            event.getPlayer().getInstance().eventNode().removeListener(breakListen.get());
        }));

        event.getPlayer().getInstance().eventNode().addListener(breakListen.get());
    }

    private void onBlockBreak(@NotNull PlayerBlockBreakEvent event) {
        if (!config.variant.allowArenaBreak()) event.setCancelled(true);
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
        e.victim().setInvisible(true);

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
