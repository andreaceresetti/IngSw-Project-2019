package model.cards;

import model.player.Player;

import java.util.ArrayList;
import java.util.List;

public class Target {
    /**
     * Target object is possessed by the effect of a weapon and changed for every attack
     */
    private List<Player> target;

    public Target() {
        this.target = new ArrayList<>();
    }

    public List<Player> getTarget() {
        return target;
    }

    public void setTarget(ArrayList<Player> target) {
        this.target = target;
    }

    public void addTargetPlayer(Player player) {
        this.target.add(player);
    }
}
