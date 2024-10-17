package net.mangolise.duels;

import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Team {
    private final List<Player> members;
    private final List<Player> remainingPlayers = new ArrayList<>();

    public Team(List<Player> members) {
        this.members = Collections.unmodifiableList(members);
        remainingPlayers.addAll(members);
    }

    /**
     * Removes player from the list of alive players.
     * @param player The player to remove.
     * @return Whether the team has been eliminated.
     */
    public boolean removeAlive(Player player) {
        remainingPlayers.remove(player);
        return remainingPlayers.isEmpty();
    }

    public List<Player> members() {
        return members;
    }
}
