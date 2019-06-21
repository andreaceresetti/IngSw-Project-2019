package network.message;

import enumerations.MessageContent;
import model.Game;
import model.player.Player;
import utility.GameCostants;

import java.util.ArrayList;
import java.util.Arrays;

public class WinnersResponse extends Message {
    private static final long serialVersionUID = -1441012929477935469L;

    private final ArrayList<Player> winners;

    public WinnersResponse(ArrayList<Player> winners) {
        super(GameCostants.GOD_NAME, null,MessageContent.WINNER);

        this.winners = winners;
    }

    public ArrayList<Player> getWinners() {
        return this.winners;
    }

    @Override
    public String toString() {
        return "WinnersResponse{" +
                "content=" + getContent() +
                ", winners=" + (winners == null ? "null" : Arrays.toString(winners.toArray())) +
                '}';
    }
}
