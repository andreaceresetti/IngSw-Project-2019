package model;

import controller.ActionManager;
import enumerations.PlayerColor;
import enumerations.GameState;
import enumerations.SquareType;
import exceptions.game.*;
import exceptions.map.InvalidPlayerPositionException;
import model.cards.AmmoTile;
import model.cards.Deck;
import model.cards.WeaponCard;
import model.map.CardSquare;
import model.map.GameMap;
import model.map.SpawnSquare;
import model.map.Square;
import model.player.*;
import utility.AmmoTileParser;
import utility.GameConstants;
import utility.PowerupParser;
import utility.WeaponParser;
import utility.persistency.NotTransientPlayer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Game implements Serializable {
    public static final Random rand = new Random();
    private static final int MAX_KILLSHOT = 8;
    private static final long serialVersionUID = -7643292361816314018L;

    private static Game instance;

    private GameState currentState;
    private boolean gameStarted;

    private List<UserPlayer> players;
    private boolean botPresent;
    private Player bot;

    private int killShotNum;
    private KillShot[] killShotsTrack;
    private ArrayList<KillShot> finalFrenzyKillShots;
    private final List<Integer> trackerPoints = new ArrayList<>(Arrays.asList(8, 6, 4, 2, 1, 1));

    private Deck weaponsCardsDeck;
    private Deck powerupCardsDeck;
    private Deck ammoTileDeck;

    private GameMap gameMap;

    /**
     * Initializes singleton Game instance
     */
    private Game() {
        init();
    }

    /**
     * Game initialization
     */
    public void init() {
        players = new ArrayList<>();
        bot = null;
        this.currentState = GameState.NORMAL;
        killShotsTrack = new KillShot[MAX_KILLSHOT];
        finalFrenzyKillShots = new ArrayList<>();
        botPresent = false;
        gameStarted = false;
        killShotNum = 0;

        weaponsCardsDeck = null;
        powerupCardsDeck = null;
        ammoTileDeck = null;
        gameMap = null;
    }

    /**
     * Game load initialization
     */
    public void loadGame(Game savedGame, List<NotTransientPlayer> notTransientPlayers) {
        currentState = savedGame.currentState;
        gameStarted = savedGame.gameStarted;
        players = savedGame.players;
        loadTransientPlayers(notTransientPlayers);
        botPresent = savedGame.botPresent;
        if(botPresent) {
            bot = savedGame.bot;
            loadTransientBot(notTransientPlayers);
        }
        killShotNum = savedGame.killShotNum;
        killShotsTrack = savedGame.killShotsTrack;
        finalFrenzyKillShots = savedGame.finalFrenzyKillShots;

        weaponsCardsDeck = savedGame.weaponsCardsDeck;
        powerupCardsDeck = savedGame.powerupCardsDeck;
        ammoTileDeck = savedGame.ammoTileDeck;

        gameMap = savedGame.gameMap;
    }

    /**
     * Method that reloads all the transient attributes of each player in the saved {@link Game Game}
     *
     * @param notTransientPlayers ArrayList of objects containing all the needed information to reset each {@link UserPlayer player} old state
     */
    private void loadTransientPlayers(List<NotTransientPlayer> notTransientPlayers) {
        for(UserPlayer player : players) {
            for(NotTransientPlayer notTransientPlayer : notTransientPlayers) {
                if(player.getUsername().equals(notTransientPlayer.getUserName())) {
                    player.setPoints(notTransientPlayer.getPoints());
                    player.setPossibleActions(notTransientPlayer.getPossibleActions());
                    player.setPlayerState(notTransientPlayer.getPlayerState());
                    player.setPowerups(notTransientPlayer.getPowerups());
                    player.setSpawningCard(notTransientPlayer.getSpawningCard());
                }
            }
        }
    }

    /**
     * Method that reloads the only transient attribute of the {@link Bot Terminator} that are his points
     *
     * @param notTransientPlayers ArrayList of objects containing also the information for the {@link Bot Terminator}'s old state
     */
    private void loadTransientBot(List<NotTransientPlayer> notTransientPlayers) {
        for(NotTransientPlayer notTransientPlayer : notTransientPlayers) {
            if(notTransientPlayer.getUserName().equals(GameConstants.BOT_NAME)) {
                bot.setPoints(notTransientPlayer.getPoints());
            }
        }
    }

    /**
     * The singleton instance of the game returns, if it has not been created it allocates it as well
     *
     * @return the singleton instance
     */
    public static Game getInstance() {
        if (instance == null)
            instance = new Game();
        return instance;
    }

    public List<KillShot> getFinalFrenzyKillShots() {
        return this.finalFrenzyKillShots;
    }

    public Integer[] getTrackerPoints() {
        return trackerPoints.toArray(new Integer[0]);
    }

    /**
     * @return an ArrayList representation of the killshotTrack
     */
    public List<KillShot> getKillShotTrack() {
        ArrayList<KillShot> arrayListTracker = new ArrayList<>();
        for (KillShot killShot : killShotsTrack) {
            if (killShot != null) {
                arrayListTracker.add(killShot);
            }
        }

        return arrayListTracker;
    }

    /**
     * @return the current state Game
     */
    public GameState getState() {
        return this.currentState;
    }

    /**
     * Method that sets the current state of the game
     *
     * @param currentState GameState to be changed
     */
    public void setState(GameState currentState) {
        this.currentState = currentState;
    }

    public void setGameMap(int mapType) throws InvalidMapNumberException {
        if (mapType < 1 || mapType > 4) {
            throw new InvalidMapNumberException();
        }
        this.gameMap = new GameMap(mapType);
    }

    public void setKillShotNum(int killShotNum) throws InvalidKillshotNumberException {
        if (killShotNum < 5 || killShotNum > 8) {
            throw new InvalidKillshotNumberException();
        }

        this.killShotNum = killShotNum;
    }

    /**
     * @return the instance of the PowerupDeck
     */
    public Deck getPowerupCardsDeck() {
        return this.powerupCardsDeck;
    }

    /**
     * Adds a player to the game
     *
     * @param player the player to add to the game
     * @throws GameAlreadyStartedException if the game has already gameStarted
     * @throws MaxPlayerException          if the maximum number of players has been reached
     */
    public void addPlayer(UserPlayer player) {
        if (gameStarted)
            throw new GameAlreadyStartedException("It is not possible to add a player when the game has already gameStarted");
        if (player == null) throw new NullPointerException("Player cannot be null");
        if (players.size() >= 5 || (players.size() >= 4 && botPresent)) throw new MaxPlayerException();
        players.add(player);
    }

    /**
     * Number of players added to the game
     *
     * @return number of players
     */
    public int playersNumber() {
        return players.size();
    }

    /**
     * Checks if game is ready to start
     *
     * @return {@code true} if the game is ready {@code false} otherwise
     */
    public boolean isGameReadyToStart() {

        if (players.size() < 3) return false;
        if (killShotNum == 0) return false;

        if (isBotPresent() && players.size() < 5) {
            return true;
        } else return (!isBotPresent() && players.size() < 6);
    }

    /**
     * Starts the game
     */
    public void startGame() {
        if (gameStarted) throw new GameAlreadyStartedException("The game is already in progress");

        gameStarted = true;

        initializeDecks();
        pickFirstPlayer();

        for (UserPlayer player : players) {
            ActionManager.setStartingPossibleActions(player, botPresent);
        }

        distributeCards();
    }

    /**
     * Picks the first player and reorders the players list
     */
    private void pickFirstPlayer() {
        int first = (new Random()).nextInt(players.size());
        players.get(first).setFirstPlayer();

        List<UserPlayer> newPlayerList = new ArrayList<>();

        for (int i = first; i < players.size(); ++i) {
            newPlayerList.add(players.get(i));
        }

        for (int i = 0; i < first; ++i) {
            newPlayerList.add(players.get(i));
        }

        players = newPlayerList;
    }

    /**
     * Distributes cards on every Square
     */
    private void distributeCards() {
        for (int i = 0; i < GameMap.MAX_ROWS; ++i) {
            for (int j = 0; j < GameMap.MAX_COLUMNS; ++j) {
                Square square = gameMap.getSquare(i, j);

                if (square != null) {
                    placeCardOnSquare(square);
                }
            }
        }
    }

    /**
     * Places card(s) of right type on a square
     *
     * @param square where card(s) must be placed
     */
    private void placeCardOnSquare(Square square) {
        if (square.getSquareType() == SquareType.SPAWN) {
            SpawnSquare spawnSquare = (SpawnSquare) square;

            for (int k = 0; k < 3; ++k) {
                spawnSquare.addWeapon((WeaponCard) weaponsCardsDeck.draw());
            }
        } else {
            CardSquare cardSquare = (CardSquare) square;

            cardSquare.setAmmoTile((AmmoTile) ammoTileDeck.draw());
        }
    }

    /**
     * Initializes the three decks: {@code weaponsCardDeck}, {@code ammoTileDeck} and {@code powerupCardsDeck}
     */
    public void initializeDecks() {
        this.weaponsCardsDeck = WeaponParser.parseCards();
        this.ammoTileDeck = AmmoTileParser.parseCards();
        this.powerupCardsDeck = PowerupParser.parseCards();
    }

    public void stopGame() {
        if (!gameStarted) throw new GameAlreadyStartedException("The game is not in progress");

        init();
    }

    /**
     * Returns the number of skulls remaining during the game
     *
     * @return the number of remaining skulls
     */
    public int remainingSkulls() {
        int leftSkulls = 0;

        for (int i = 0; i < killShotNum; i++) {
            if (killShotsTrack[i] == null) leftSkulls++;
        }

        return leftSkulls;
    }

    /**
     * Adds a killshot to the game by removing a skull
     *
     * @param killShot killshot to add
     */
    public void addKillShot(KillShot killShot) {
        if (killShot == null) throw new NullPointerException("Killshot cannot be null");

        for (int i = 0; i < killShotNum; i++) {
            if (killShotsTrack[i] == null) {
                killShotsTrack[i] = killShot;
                return;
            }
        }

        throw new KillShotsTerminatedException();
    }

    public boolean isBotPresent() {
        return this.botPresent;
    }

    /**
     * Only sets true to the boolean attribute that specifies the presence of the bot
     *
     * @param terminatorPresent true in case the bot is present, otherwise false
     * @throws MaxPlayerException in case the game already has 5 players
     */
    public void setBot(boolean terminatorPresent) {
        if (gameStarted)
            throw new GameAlreadyStartedException("It is not possible to set the setBot player when the game has already gameStarted.");
        if (players.size() >= 5 && terminatorPresent)
            throw new MaxPlayerException("Can not add Terminator with 5 players");
        this.botPresent = terminatorPresent;
    }

    /**
     * Method to spawn the bot, separated as it can be spawned after the spawn of the players
     */
    public void buildTerminator() {
        this.bot = new Bot(firstColorUnused(), new PlayerBoard());
    }

    /**
     * Find the first color that no player uses, otherwise {@code null}
     *
     * @return the first color not used by any player
     */
    private PlayerColor firstColorUnused() {
        ArrayList<PlayerColor> ar = new ArrayList<>();

        for (UserPlayer player : players) {
            ar.add(player.getColor());
        }

        for (int i = 0; i < PlayerColor.values().length; i++) {
            if (!ar.contains(PlayerColor.values()[i])) return PlayerColor.values()[i];
        }

        return null;
    }

    /**
     * Spawn the player to a spawn point on the map
     *
     * @param player         the player to spawn
     * @param playerPosition the player's spawn position
     */
    public void spawnPlayer(UserPlayer player, PlayerPosition playerPosition) {
        if (player == null || playerPosition == null)
            throw new NullPointerException("Player or playerPosition cannot be null");
        if (!players.contains(player)) throw new UnknownPlayerException();
        if (!gameStarted) throw new GameAlreadyStartedException("Game not gameStarted yet");

        try {
            Square temp = gameMap.getSquare(playerPosition);

            if (temp == null) {
                throw new InvalidPlayerPositionException();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new InvalidPlayerPositionException();
        }

        player.setPosition(playerPosition);
    }

    /**
     * Spawn the bot player to a spawn point on the map
     *
     * @param playerPosition the player's spawn position
     * @throws GameAlreadyStartedException if the game has not gameStarted
     */
    public void spawnTerminator(PlayerPosition playerPosition) {
        if (playerPosition == null) throw new NullPointerException("playerPosition cannot be null");
        if (!gameStarted) throw new GameAlreadyStartedException("Game not gameStarted yet");
        if (!botPresent) throw new BotNotSetException();

        bot.setPosition(playerPosition);
    }

    /**
     * Function that returns true if the game gameStarted, otherwise false
     *
     * @return true if the game gameStarted, otherwise false
     */
    public boolean isGameStarted() {
        return gameStarted;
    }

    /**
     * The number of killshot for this Game
     *
     * @return number of killshot set
     */
    int getKillShotNum() {
        return killShotNum;
    }

    /**
     * Return the instance of bot {@code player} for this game
     *
     * @return the bot instance
     */
    public Player getBot() {
        return (botPresent) ? bot : null;
    }

    /**
     * @return the GameMap
     */
    public GameMap getGameMap() {
        return gameMap;
    }

    /**
     * @return the List of players in the game
     */
    public List<UserPlayer> getPlayers() {
        return players;
    }

    /**
     * Method to obtain the UserPlayer with the specified ID
     *
     * @param username of the desired player
     * @return the UserPlayer with the ID passed
     */
    public Player getUserPlayerByUsername(String username) {
        if (username.equalsIgnoreCase(GameConstants.BOT_NAME)) {
            return bot;
        }

        for (UserPlayer p : players) {
            if (p.getUsername().equals(username)) return p;
        }

        throw new MissingPlayerUsernameException(username);
    }

    public boolean doesPlayerExists(String username) {
        for (UserPlayer p : players) {
            if (p.getUsername().equals(username)) return true;
        }
        return false;
    }

    /**
     * Method to obtain the player that gameStarted to play the game
     *
     * @return the UserPLayer who gameStarted the game
     */
    public UserPlayer getFirstPlayer() {
        for (UserPlayer player : players) {
            if (player.isFirstPlayer()) {
                return player;
            }
        }

        throw new NoFirstPlayerException();
    }

    /**
     * Method that returns the death players in the game at the moment it is called
     *
     * @return an ArrayList containing all dead players
     */
    public List<UserPlayer> getDeathPlayers() {
        ArrayList<UserPlayer> deathPlayers = new ArrayList<>();

        for (UserPlayer player : players) {
            if (player.getPlayerBoard().getDamageCount() > 10) {
                deathPlayers.add(player);
            }
        }

        return deathPlayers;
    }

    /**
     * @return an ArrayList containing the damage of each {@link UserPlayer UserPlayer's} {@link PlayerBoard PlayerBoard}
     */
    public List<Integer> getPlayersDamage() {
        List<Integer> playersDamage = new ArrayList<>();

        for(UserPlayer player : players) {
            playersDamage.add(player.getPlayerBoard().getDamageCount());
        }

        if(isBotPresent()) {
            playersDamage.add(bot.getPlayerBoard().getDamageCount());
        }

        return playersDamage;
    }

    public Deck getWeaponsCardsDeck() {
        return weaponsCardsDeck;
    }

    public Deck getAmmoTileDeck() {
        return ammoTileDeck;
    }

    KillShot[] getKillShotsTrack() {
        return killShotsTrack;
    }

    /**
     * Method that return the player with username {@code username}, if the player is not present returns {@code null}
     *
     * @param username the username of searching player
     * @return the player with {@code username} username, otherwhise null
     */
    public Player getPlayerByName(String username) {
        for (Player player : players) {
            if (player.getUsername().equals(username)) return player;
        }

        if (bot.getUsername().equals(username)) return bot;

        return null;
    }
}
