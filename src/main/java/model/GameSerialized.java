package model;

import enumerations.GameState;
import model.map.GameMap;
import model.player.KillShot;
import model.player.Player;
import model.player.Terminator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class GameSerialized implements Serializable  {

    private GameState currentState;
    private GameMap gameMap;

    private ArrayList<Player> players;
    private boolean terminatorPresent;

    private int killShotNum;
    private KillShot[] killShotsTrack;

    public GameSerialized() {
        Game instance = Game.getInstance();

        currentState = instance.getState();
        players = new ArrayList<>(instance.getPlayers());

        terminatorPresent = instance.isTerminatorPresent();
        if (terminatorPresent) players.add(instance.getTerminator());

        killShotsTrack = instance.getKillShotsTrack() != null ? Arrays.copyOf(instance.getKillShotsTrack(), instance.getKillShotsTrack().length) : null;
        killShotNum = instance.getKillShotNum();
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(GameState currentState) {
        this.currentState = currentState;
    }

    public ArrayList<Player> getPlayers() {
        return players;
    }

    public void setPlayers(ArrayList<Player> players) {
        this.players = players;
    }

    public void setPlayerBoards(ArrayList<Player> players) {
        this.players = players;
    }

    public boolean isTerminatorPresent() {
        return terminatorPresent;
    }

    public void setTerminatorPresent(boolean terminatorPresent) {
        this.terminatorPresent = terminatorPresent;
    }

    public int getKillShotNum() {
        return killShotNum;
    }

    public void setKillShotNum(int killShotNum) {
        this.killShotNum = killShotNum;
    }

    public KillShot[] getKillShotsTrack() {
        return killShotsTrack;
    }

    public void setKillShotsTrack(KillShot[] killShotsTrack) {
        this.killShotsTrack = killShotsTrack;
    }

    public GameMap getGameMap() {
        return gameMap;
    }

    public void setGameMap(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    //utility method for cli debugging
    public void setTerminator(Terminator terminator) {
        players.add(terminator);
    }

    @Override
    public String toString() {
        return "GameSerialized{" +
                "currentState=" + currentState +
                ", players=" + players +
                ", terminatorPresent=" + terminatorPresent +
                ", killShotNum=" + killShotNum +
                ", killShotsTrack=" + Arrays.toString(killShotsTrack) +
                '}';
    }
}
