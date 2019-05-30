package model.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enumerations.RoomColor;
import enumerations.SquareAdjacency;
import enumerations.SquareType;
import exceptions.file.JsonFileNotFoundException;
import exceptions.map.InvalidSpawnColorException;
import exceptions.map.MapUnknowException;
import model.Game;
import model.cards.AmmoTile;
import model.cards.WeaponCard;
import model.player.Player;
import model.player.PlayerPosition;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GameMap implements Serializable {
    /**
     * Maximum number of map lines: 3
     */
    public static final int MAX_ROWS = 3;
    /**
     * Maximum number of map columns: 4
     */
    public static final int MAX_COLUMNS = 4;
    /**
     * Map of type:
     * B B B
     * R R R Y
     * W W Y
     */
    public static final int MAP_1 = 1;
    /**
     * Map of type:
     * B B B G
     * R R Y Y
     * W Y Y
     */
    public static final int MAP_2 = 2;
    /**
     * Map of type:
     * R B B G
     * R M Y Y
     * W W Y Y
     */
    public static final int MAP_3 = 3;
    /**
     * Map of type:
     * R B B
     * R M M Y
     * W W W Y
     */
    public static final int MAP_4 = 4;

    private Square[][] rooms;

    public GameMap(int mapType) {
        String path = "json/maps.json";
        InputStream is = GameMap.class.getClassLoader().getResourceAsStream(path);

        if (is == null) throw new JsonFileNotFoundException("File " + path + " not found");

        JsonParser parser = new JsonParser();
        JsonArray array = parser.parse(new InputStreamReader(is)).getAsJsonArray();

        JsonObject mapObject;

        Square[][] map;
        map = new Square[MAX_ROWS][MAX_COLUMNS];

        switch (mapType) {
            case MAP_1:
                mapObject = getMapObject(array, MAP_1);
                break;

            case MAP_2:
                mapObject = getMapObject(array, MAP_2);
                break;

            case MAP_3:
                mapObject = getMapObject(array, MAP_3);
                break;

            case MAP_4:
                mapObject = getMapObject(array, MAP_4);
                break;

            default:
                throw new MapUnknowException();
        }

        JsonArray matrix = mapObject.get("map").getAsJsonArray();

        fillMap(matrix, map);

        this.rooms = map;
    }

    private static void fillMap(JsonArray matrix, Square[][] map) {
        for (int i = 0; i < matrix.size(); i++) {
            JsonArray row = matrix.get(i).getAsJsonArray();
            for (int j = 0; j < row.size(); j++) {
                if (row.get(j).isJsonNull()) {
                    map[i][j] = null;
                } else {
                    JsonObject square = row.get(j).getAsJsonObject();
                    if (square.get("isSpawn").getAsBoolean()) {
                        map[i][j] = new SpawnSquare(
                                RoomColor.valueOf(square.get("color").getAsString()),
                                SquareAdjacency.valueOf(square.get("north").getAsString()),
                                SquareAdjacency.valueOf(square.get("east").getAsString()),
                                SquareAdjacency.valueOf(square.get("south").getAsString()),
                                SquareAdjacency.valueOf(square.get("west").getAsString())
                        );
                    } else {
                        map[i][j] = new CardSquare(
                                RoomColor.valueOf(square.get("color").getAsString()),
                                SquareAdjacency.valueOf(square.get("north").getAsString()),
                                SquareAdjacency.valueOf(square.get("east").getAsString()),
                                SquareAdjacency.valueOf(square.get("south").getAsString()),
                                SquareAdjacency.valueOf(square.get("west").getAsString())
                        );
                    }
                }
            }
        }
    }

    private static JsonObject getMapObject(JsonArray array, int mapType) {
        JsonObject mapObject = null;
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonNull() && array.get(i).getAsJsonObject().get("id").getAsInt() == mapType) {
                mapObject = array.get(i).getAsJsonObject();
            }
        }
        if (mapObject != null) return mapObject;
        throw new NullPointerException("Something went wrong... mapType: " + mapType + " JsonArray: " + array);
    }

    /**
     * Returns the square specifying his coordinates
     *
     * @param x the X of the square
     * @param y the Y of the square
     * @return the Square whose coordX is x and coordY is y
     */
    public Square getSquare(int x, int y) {
        return rooms[x][y];
    }

    /**
     * Returns the square specifying a playerPosition
     *
     * @param playerPosition position of a player
     * @return the Square represented by the playerPosition
     */
    public Square getSquare(PlayerPosition playerPosition) {
        return rooms[playerPosition.getCoordX()][playerPosition.getCoordY()];
    }

    /**
     * Method to obtain all the players who are in the specified position
     *
     * @param pos the position in which there are the Players returned
     * @return the players who are in the position pos
     */
    public List<Player> getPlayersInSquare(PlayerPosition pos) {
        Game game = Game.getInstance();
        List<Player> players = new ArrayList<>();

        for (Player p : game.getPlayers()) {
            if (p.getPosition() != null && p.getPosition().equals(pos)) {
                players.add(p);
            }
        }

        if (game.isTerminatorPresent()) {
            Player term = game.getTerminator();
            if (term.getPosition().equals(pos)) {
                players.add(term);
            }
        }

        return players;
    }

    /**
     * Method to obtain all the players who are in the specified room
     *
     * @param roomColor the Color of the room in which there are the players returned
     * @return the ArrayList of players who are in the room of color roomColor
     */
    public List<Player> getPlayersInRoom(RoomColor roomColor) {
        Game game = Game.getInstance();
        List<Player> players = new ArrayList<>();

        for (Player p : game.getPlayers()) {
            if (getSquare(p.getPosition().getCoordX(), p.getPosition().getCoordY()).getRoomColor().equals(roomColor)) {
                players.add(p);
            }
        }

        if (game.isTerminatorPresent()) {
            Player term = game.getTerminator();
            if (getSquare(term.getPosition().getCoordX(), term.getPosition().getCoordY()).getRoomColor().equals(roomColor)) {
                players.add(term);
            }
        }

        return players;
    }

    /**
     * Method to obtain all the squares of the same room
     *
     * @param roomColor the Color of the room
     * @return an ArrayList of all the squares with the same Color
     */
    public List<PlayerPosition> getRoom(RoomColor roomColor) {
        List<PlayerPosition> room = new ArrayList<>();

        for (int i = 0; i < MAX_ROWS; ++i) {
            for (int j = 0; j < MAX_COLUMNS; ++j) {
                if (rooms[i][j] != null && rooms[i][j].getRoomColor().equals(roomColor)) {
                    PlayerPosition tempPos = new PlayerPosition(i, j);
                    room.add(tempPos);
                }
            }
        }

        return room;
    }

    /**
     * Method that returns the spawn position of the spawn square of the specified color
     *
     * @param spawnColor the color of the square where to spawn
     * @return the playerposition of the square whre to spawn
     * @throws InvalidSpawnColorException if color chosen does not correspond to a spawn one
     */
    public PlayerPosition getSpawnSquare(RoomColor spawnColor) throws InvalidSpawnColorException {
        List<PlayerPosition> room = getRoom(spawnColor);
        for (PlayerPosition spawnPosition : room) {
            if (getSquare(spawnPosition).getSquareType().equals(SquareType.SPAWN)) {
                return spawnPosition;
            }
        }

        // spawn colors are always RED,BLUE and YELLOW
        throw new InvalidSpawnColorException();
    }

    public Square[][] getRooms() {
        return rooms;
    }

    /**
     * Method used at the end of the turn of each player to set the missing cards back on the map
     */
    public void addMissingCards() {
        for (int i = 0; i < MAX_ROWS; ++i) {
            for (int j = 0; j < MAX_COLUMNS; ++j) {
                Square tempSquare = getSquare(i, j);
                if (tempSquare.getSquareType() == SquareType.TILE) {
                    if (!((CardSquare) tempSquare).isAmmoTilePresent()) {
                        ((CardSquare) tempSquare).setAmmoTile((AmmoTile) Game.getInstance().getAmmoTileDeck().draw());
                    }
                } else if (tempSquare.getSquareType() == SquareType.SPAWN) {
                    if (((SpawnSquare) tempSquare).getWeapons().length < 3) {
                        addMissingWeapons((SpawnSquare) tempSquare);
                    }
                } else {
                    throw new NullPointerException("A Square must always have a type!");
                }
            }
        }
    }

    /**
     * Method used to reduce cognitive complexity when we need to add more than just one missing card to a Spawn Square
     *
     * @param missingSquare SpawnSquare on which 1 or more Weapon is missing
     */
    private void addMissingWeapons(SpawnSquare missingSquare) {     // remember: on a square can stay maximum 3 cards!
        for (int i = 0; i < (3 - missingSquare.getWeapons().length); ++i) {
            missingSquare.addWeapon((WeaponCard) Game.getInstance().getWeaponsCardsDeck().draw());
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();

        buffer.append('\n');

        for (int i = 0; i < MAX_ROWS; i++) {
            for (int j = 0; j < MAX_COLUMNS; j++) {
                if (rooms[i][j] == null) {
                    buffer.append(" \t");
                } else {
                    buffer.append(' ');
                    buffer.append(rooms[i][j].getRoomColor());
                }
            }
            buffer.append('\n');
        }

        return buffer.toString();
    }
}