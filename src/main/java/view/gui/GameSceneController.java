package view.gui;

import enumerations.*;
import exceptions.actions.PowerupCardsNotFoundException;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.GameSerialized;
import model.cards.PowerupCard;
import model.cards.WeaponCard;
import model.map.CardSquare;
import model.map.GameMap;
import model.map.SpawnSquare;
import model.map.Square;
import model.player.*;
import utility.GameCostants;
import utility.MessageBuilder;

import java.util.*;

import static network.client.ClientGameManager.SEND_ERROR;

public class GameSceneController {
    private static final String USERNAME_PROPERTY = "username";
    private static final double OPAQUE = 0.3;
    private static final double NOT_OPAQUE = 1;

    private static final double KILLSHOT_TRACK_SKULL_WIDTH = 20;
    private static final double KILLSHOT_TRACK_SKULL_HEIGHT = 28;

    private static final double PLAYER_BOARD_WIDTH = 680;
    private static final double PLAYER_BOARD_HEIGHT = 166;

    private static final double PLAYER_BOARD_SKULL_WIDTH = 25;
    private static final double PLAYER_BOARD_SKULL_HEIGHT = 38;

    private static final double WEAPON_CARD_WIDTH = 136;
    private static final double WEAPON_CARD_HEIGHT = 230;

    private static final double POWERUP_CARD_WIDTH = 128;
    private static final double POWERUP_CARD_HEIGHT = 200;


    private static final double OPACITY_VALUE = 0.2;

    private GuiManager guiManager;

    private List<ImageView> weaponSlotList;
    private List<ImageView> ammoTiles;
    private List<ImageView> killshotsImages;
    private List<ImageView> playerFigures;
    private Map<String, Ammo> weaponColor;

    @FXML
    Pane mainPane;
    @FXML
    StackPane boardArea;
    @FXML
    ImageView map;
    @FXML
    ImageView powerupDeck;
    @FXML
    ImageView weaponDeck;
    @FXML
    ImageView blueWeapon0;
    @FXML
    ImageView blueWeapon1;
    @FXML
    ImageView blueWeapon2;
    @FXML
    ImageView redWeapon0;
    @FXML
    ImageView redWeapon1;
    @FXML
    ImageView redWeapon2;
    @FXML
    ImageView yellowWeapon0;
    @FXML
    ImageView yellowWeapon1;
    @FXML
    ImageView yellowWeapon2;
    @FXML
    VBox iconList;
    @FXML
    VBox actionList;
    @FXML
    FlowPane zoomPanel;
    @FXML
    BorderPane infoPanel;
    @FXML
    StackPane spawnPanel;

    @FXML
    private void initialize() {
        guiManager = GuiManager.getInstance();
        guiManager.setGameSceneController(this);

        ammoTiles = new ArrayList<>();
        playerFigures = new ArrayList<>();
        killshotsImages = new ArrayList<>();
        weaponSlotList = List.of(blueWeapon0, blueWeapon1, blueWeapon2, redWeapon0, redWeapon1, redWeapon2,
                yellowWeapon0, yellowWeapon1, yellowWeapon2);
    }


    void setupGame(GameSerialized gameSerialized) {
        GameMap gameMap = gameSerialized.getGameMap();

        map.setImage(new Image(gameMap.getImagePath()));

        setPlayerIcons(gameSerialized);

        bindWeaponZoom();
        bindPanels();

        updateMap(gameSerialized);
    }

    private void bindPanels() {
        infoPanel.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> hideInfoPanel());
        zoomPanel.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> hideZoomPanel());
        spawnPanel.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> hideSpawnPanel());
    }

    private void hideSpawnPanel() {
        spawnPanel.getChildren().clear();
        spawnPanel.setVisible(false);

        setBoardOpaque(NOT_OPAQUE);
    }

    /**
     * Binds weapon zoom on card click
     */
    private void bindWeaponZoom() {
        for (ImageView weaponSlot : weaponSlotList) {
            weaponSlot.addEventHandler(MouseEvent.MOUSE_CLICKED, this::showWeaponZoom);
        }
    }

    private void setPlayerIcons(GameSerialized gameSerialized) {
        ImageView imageView;

        for (UserPlayer player : gameSerialized.getPlayers()) {
            imageView = new ImageView();
            imageView.setId(getIconIDFromColor(player.getColor()));
            imageView.getProperties().put(USERNAME_PROPERTY, player.getUsername());

            iconList.getChildren().add(imageView);
            imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, this::showPlayerInfo);
        }

        if (gameSerialized.isBotPresent()) {
            imageView = new ImageView();
            imageView.setId(getIconIDFromColor(gameSerialized.getBot().getColor()));
            imageView.getProperties().put(USERNAME_PROPERTY, "bot");

            iconList.getChildren().add(imageView);
            imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, this::showPlayerInfo);
        }
    }

    private String getIconIDFromColor(PlayerColor playerColor) {
        switch (playerColor) {
            case BLUE:
                return "blueIcon";
            case YELLOW:
                return "yellowIcon";
            case GREEN:
                return "greenIcon";
            case GREY:
                return "greyIcon";
            case PURPLE:
                return "purpleIcon";
            default:
                return null;
        }
    }

    void setTurnOwnerIcon(String turnOwner) {
        for (Node children : iconList.getChildren()) {
            children.getStyleClass().clear();

            String iconOwner = (String) children.getProperties().get(USERNAME_PROPERTY);

            if (iconOwner.equals(turnOwner)) {
                children.getStyleClass().add("turnOwner");
            } else {
                children.getStyleClass().add("notTurnOwner");
            }
        }
    }

    void onStateUpdate(GameSerialized gameSerialized) {
        setTurnOwnerIcon(GuiManager.getInstance().getTurnOwner());
        updateMap(gameSerialized);
    }

    /**
     * Updates element on the map
     *
     * @param gameSerialized game update
     */
    private void updateMap(GameSerialized gameSerialized) {
        setWeaponCards(gameSerialized.getGameMap());
        setPlayersOnMap(gameSerialized.getGameMap().getMapID(), gameSerialized.getAllPlayers());
        setAmmoTiles(gameSerialized.getGameMap());
        setKillshotTrack(gameSerialized);
    }

    /**
     * Sets weapon cards on the map
     *
     * @param gameMap map of the game
     */
    private void setWeaponCards(GameMap gameMap) {
        List<WeaponCard> weaponCards;
        weaponColor = new HashMap<>();

        SpawnSquare spawnSquare =
                (SpawnSquare) gameMap.getSquare(gameMap.getSpawnSquare(RoomColor.BLUE));
        weaponCards = new ArrayList<>(Arrays.asList(spawnSquare.getWeapons()));

        spawnSquare =
                (SpawnSquare) gameMap.getSquare(gameMap.getSpawnSquare(RoomColor.RED));
        weaponCards.addAll(Arrays.asList(spawnSquare.getWeapons()));

        spawnSquare =
                (SpawnSquare) gameMap.getSquare(gameMap.getSpawnSquare(RoomColor.YELLOW));
        weaponCards.addAll(Arrays.asList(spawnSquare.getWeapons()));

        for (int i = 0; i < weaponSlotList.size(); ++i) {
            Image image;

            if (weaponCards.get(i) != null) {
                image = new Image(weaponCards.get(i).getImagePath());
                weaponSlotList.get(i).setImage(image);
                weaponColor.put(image.getUrl(), weaponCards.get(i).getCost()[0]);
            } else {
                weaponSlotList.get(i).setImage(null);
            }

        }
    }

    /**
     * Sets ammo tiles on the map
     *
     * @param gameMap map of the game
     */
    private void setAmmoTiles(GameMap gameMap) {
        for (ImageView ammoTile : ammoTiles) {
            boardArea.getChildren().remove(ammoTile);
        }
        ammoTiles.clear();

        for (int y = 0; y < GameMap.MAX_COLUMNS; ++y) {
            for (int x = 0; x < GameMap.MAX_ROWS; ++x) {
                Square square = gameMap.getSquare(x, y);
                if (square != null && square.getSquareType() == SquareType.TILE) {
                    CardSquare cardSquare = (CardSquare) square;

                    ImageView ammoTile = (cardSquare.isAmmoTilePresent()) ?
                            new ImageView(cardSquare.getAmmoTile().getImagePath()) : new ImageView();

                    ammoTile.setFitHeight(32);
                    ammoTile.setFitWidth(32);

                    StackPane.setAlignment(ammoTile, Pos.TOP_LEFT);
                    StackPane.setMargin(ammoTile, MapInsetsHelper.getAmmoTileInsets(gameMap.getMapID(), x, y));

                    boardArea.getChildren().add(ammoTile);
                    ammoTiles.add(ammoTile);
                }
            }
        }
    }

    /**
     * Sets players on the map
     *
     * @param mapID      id of the map
     * @param allPlayers list of players
     */
    private void setPlayersOnMap(int mapID, ArrayList<Player> allPlayers) {
        for (ImageView playerFigure : playerFigures) {
            boardArea.getChildren().remove(playerFigure);
        }

        playerFigures.clear();

        for (int i = 0; i < allPlayers.size(); ++i) {
            Player player = allPlayers.get(i);

            if (player.getPosition() != null) {

                int count = 0;
                for (int j = i - 1; j >= 0; --j) {
                    if (allPlayers.get(j).getPosition().equals(player.getPosition())) {
                        ++count;
                    }
                }

                ImageView playerFigure = new ImageView(getColorFigurePath(player.getColor()));

                StackPane.setAlignment(playerFigure, Pos.TOP_LEFT);
                StackPane.setMargin(playerFigure, MapInsetsHelper.getPlayerInsets(mapID, player.getPosition().getCoordX(), player.getPosition().getCoordY(), count));

                boardArea.getChildren().add(playerFigure);
                playerFigures.add(playerFigure);
            }
        }
    }

    private void setKillshotTrack(GameSerialized gameSerialized) {
        List<KillShot> killShots = new ArrayList<>(Arrays.asList(gameSerialized.getKillShotsTrack()));
        int killShotNum = gameSerialized.getKillShotNum();

        killShots.subList(killShotNum - 1 , killShots.size() - 1).clear();

        for (ImageView killShot : killshotsImages) {
            boardArea.getChildren().remove(killShot);
        }

        killshotsImages.clear();
        // TODO ADD FRENZY


        double startingLeftMargin = MapInsetsHelper.killShotTrackInsets.getLeft() + (8 - killShotNum) * MapInsetsHelper.KILLSHOT_TRACK_HORIZONTAL_OFFSET;
        double topMargin = MapInsetsHelper.killShotTrackInsets.getTop();

        for (int i = 0; i < killShots.size(); ++i) {
            KillShot killShot = killShots.get(i);
            double horizontalOffset = (killShotNum - i <= 0) ? MapInsetsHelper.KILLSHOT_TRACK_TINY_HORIZONTAL_OFFSET : MapInsetsHelper.KILLSHOT_TRACK_HORIZONTAL_OFFSET;

            if (killShot == null) {
                ImageView skull = new ImageView("/img/skull.png");
                skull.setFitWidth(KILLSHOT_TRACK_SKULL_WIDTH);
                skull.setFitHeight(KILLSHOT_TRACK_SKULL_HEIGHT);

                StackPane.setAlignment(skull, Pos.TOP_LEFT);
                StackPane.setMargin(skull, new Insets(topMargin, 0, 0, startingLeftMargin));

                boardArea.getChildren().add(skull);
                killshotsImages.add(skull);
            } else {
                PlayerColor playerColor = guiManager.getPlayerByName(killShot.getKiller()).getColor();
                String dropPath = getDropPath(playerColor);

                if (killShot.getPoints() > 1) {
                    addDropToKillshotTrack(dropPath, topMargin - MapInsetsHelper.KILLSHOT_TRACK_VERTICAL_OFFSET, startingLeftMargin);
                }

                addDropToKillshotTrack(dropPath, topMargin, startingLeftMargin);

                if (killShot.getPoints() > 2) {
                    addDropToKillshotTrack(dropPath, topMargin + MapInsetsHelper.KILLSHOT_TRACK_VERTICAL_OFFSET, startingLeftMargin);
                }
            }

            startingLeftMargin += horizontalOffset;
        }

    }

    private void addDropToKillshotTrack(String dropPath, double topMargin, double leftMargin) {
        ImageView drop = new ImageView(dropPath);
        StackPane.setAlignment(drop, Pos.TOP_LEFT);
        StackPane.setMargin(drop, new Insets(topMargin, 0, 0, leftMargin));

        boardArea.getChildren().add(drop);
        killshotsImages.add(drop);
    }

    /**
     * Returns the path of the figure image based on color
     *
     * @param playerColor color of the player
     * @return path of the figure image
     */
    private String getColorFigurePath(PlayerColor playerColor) {
        switch (playerColor) {
            case BLUE:
                return "/img/players/blueFigure.png";
            case YELLOW:
                return "/img/players/yellowFigure.png";
            case GREEN:
                return "/img/players/greenFigure.png";
            case PURPLE:
                return "/img/players/purpleFigure.png";
            case GREY:
                return "/img/players/greyFigure.png";
            default:
                return "";
        }
    }

    /**
     * Shows the zoom on a weapon in the zoom panel
     *
     * @param event of the click on a weapon
     */
    private void showWeaponZoom(Event event) {
        ImageView weaponTarget = (ImageView) event.getTarget();

        if (weaponTarget != null) {
            setBoardOpaque(OPACITY_VALUE);

            zoomPanel.toFront();
            ImageView weapon = new ImageView(weaponTarget.getImage());

            Ammo color = weaponColor.get(weaponTarget.getImage().getUrl());
            if (color != null) {
                String className = null;

                switch (color) {
                    case BLUE:
                        className = "weaponZoomImageBlue";
                        break;
                    case RED:
                        className = "weaponZoomImageRed";
                        break;
                    case YELLOW:
                        className = "weaponZoomImageYellow";
                        break;
                }

                weapon.getStyleClass().add(className);

                zoomPanel.getChildren().add(weapon);
                zoomPanel.setVisible(true);
            }
        }
    }

    /**
     * Hides the zoom panel
     */
    private void hideZoomPanel() {
        zoomPanel.getChildren().clear();
        zoomPanel.setVisible(false);

        setBoardOpaque(1);
    }

    /**
     * Sets a opacity value for every element on the board
     *
     * @param value opacity value
     */
    private void setBoardOpaque(double value) {
        map.opacityProperty().setValue(value);
        powerupDeck.opacityProperty().setValue(value);
        weaponDeck.opacityProperty().setValue(value);
        blueWeapon0.opacityProperty().setValue(value);
        blueWeapon1.opacityProperty().setValue(value);
        blueWeapon2.opacityProperty().setValue(value);
        redWeapon0.opacityProperty().setValue(value);
        redWeapon1.opacityProperty().setValue(value);
        redWeapon2.opacityProperty().setValue(value);
        yellowWeapon0.opacityProperty().setValue(value);
        yellowWeapon1.opacityProperty().setValue(value);
        yellowWeapon2.opacityProperty().setValue(value);

        for (ImageView ammotile : ammoTiles) {
            ammotile.opacityProperty().setValue(value);
        }

        for (ImageView playerFigure : playerFigures) {
            playerFigure.opacityProperty().setValue(value);
        }
    }

    void onError(String error) {
        GuiManager.showDialog((Stage) mainPane.getScene().getWindow(), "Error", error);
    }

    /**
     * Empties the list of action buttons
     */
    void notYourTurn(String turnOwner) {
        actionList.getChildren().clear();
        setTurnOwnerIcon(turnOwner);
    }

    /**
     * Displays action buttons
     *
     * @param possibleActions possible actions
     */
    void displayAction(List<PossibleAction> possibleActions) {
        actionList.getChildren().clear();

        for (PossibleAction possibleAction : possibleActions) {
            ImageView imageView = new ImageView();
            imageView.setId(getActionIDFromPossibleAction(possibleAction));
            imageView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> guiManager.doAction(possibleAction));
            imageView.getStyleClass().add("button");

            actionList.getChildren().add(imageView);
        }
    }

    /**
     * Returns the CSS ID of the action based on the PossibleAction
     *
     * @param possibleAction possible action passed
     * @return the CSS ID
     */
    private String getActionIDFromPossibleAction(PossibleAction possibleAction) {
        switch (possibleAction) {
            case SPAWN_BOT:
            case RESPAWN_BOT:
                return "spawnBotAction";
            case CHOOSE_SPAWN:
            case CHOOSE_RESPAWN:
                return "playerSpawnAction";
            case POWER_UP:
                return "powerupAction";
            case GRENADE_USAGE:
                return "grenadeAction";
            case SCOPE_USAGE:
                return "scopeAction";
            case MOVE:
                return "moveAction";
            case MOVE_AND_PICK:
                return "movePickAction";
            case SHOOT:
                return "shootAction";
            case RELOAD:
                return "reloadAction";
            case ADRENALINE_PICK:
                return "adrenalinePickAction";
            case ADRENALINE_SHOOT:
                return "adrenalineShootAction";
            case FRENZY_MOVE:
                return "frenzyMoveAction";
            case FRENZY_PICK:
                return "frenzyPickAction";
            case FRENZY_SHOOT:
                return "frenzyShootAction";
            case LIGHT_FRENZY_PICK:
                return "lightFrenzyPickAction";
            case LIGHT_FRENZY_SHOOT:
                return "lightFrenzyShootAction";
            case BOT_ACTION:
                return "botAction";
            case PASS_TURN:
                return "passTurnAction";
            default:
                return null;
        }
    }

    /**
     * Shows the player info in the info panel
     *
     * @param event event of the click on a icon
     */
    private void showPlayerInfo(Event event) {
        ImageView playerIcon = (ImageView) event.getTarget();
        String username = (String) playerIcon.getProperties().get(USERNAME_PROPERTY);

        if (guiManager.getUsername().equals(username)) {
            showMyPlayerInfo(guiManager.getPlayer());
        } else if (username.equals(GameCostants.BOT_NAME)) {
            showBotPlayerInfo((Bot) guiManager.getPlayerByName(username));
        } else {
            showOthersPlayerInfo((UserPlayer) guiManager.getPlayerByName(username));
        }

        setBoardOpaque(OPACITY_VALUE);
        infoPanel.toFront();
        infoPanel.setVisible(true);

    }

    private void showMyPlayerInfo(UserPlayer me) {
        setUsernamePlayerInfo(me.getUsername());

        addPlayerBoardToPlayerInfo(me);
        setDamages(me.getPlayerBoard());
        setMarks(me.getPlayerBoard());
        setAmmo(me);
        setPlayerboardSkulls(me.getPlayerBoard());

        setWeapons(Arrays.asList(me.getWeapons()));

        try {
            setPowerups(Arrays.asList(me.getPowerups()));
        } catch (NullPointerException e) {
            // No issues
        }
    }

    private void showBotPlayerInfo(Bot bot) {
        setUsernamePlayerInfo(bot.getUsername());

        addPlayerBoardToPlayerInfo(bot);
        setDamages(bot.getPlayerBoard());
        setMarks(bot.getPlayerBoard());
        setPlayerboardSkulls(bot.getPlayerBoard());
    }

    private void showOthersPlayerInfo(UserPlayer other) {
        setUsernamePlayerInfo(other.getUsername());

        addPlayerBoardToPlayerInfo(other);
        setDamages(other.getPlayerBoard());
        setMarks(other.getPlayerBoard());
        setAmmo(other);
        setPlayerboardSkulls(other.getPlayerBoard());

        setWeapons(Arrays.asList(other.getWeapons()));
    }

    private void setUsernamePlayerInfo(String username) {
        Label label = new Label(username);
        label.getStyleClass().add("infoTitle");

        VBox vBox = new VBox();
        vBox.getStyleClass().add("topInfoPanel");
        vBox.getChildren().add(label);

        infoPanel.setTop(vBox);
    }

    private void addPlayerBoardToPlayerInfo(Player player) {
        PlayerColor playerColor = player.getColor();
        PlayerBoard playerBoard = player.getPlayerBoard();

        AnchorPane anchorPane = new AnchorPane();

        ImageView playerBoardImageView = new ImageView(getPlayboardPath(playerColor, playerBoard));
        playerBoardImageView.setFitWidth(PLAYER_BOARD_WIDTH);
        playerBoardImageView.setFitHeight(PLAYER_BOARD_HEIGHT);

        AnchorPane.setLeftAnchor(playerBoardImageView, MapInsetsHelper.playerBoardInsets.getLeft());

        anchorPane.getChildren().add(playerBoardImageView);
        infoPanel.setCenter(anchorPane);
    }

    private String getPlayboardPath(PlayerColor playerColor, PlayerBoard playerBoard) {
        String suffix;

        if (playerBoard.isBoardFlipped()) {
            suffix = "back";
        } else if (guiManager.getGameClientState() == GameClientState.NORMAL) {
            suffix = "front";
        } else {
            suffix = "front_final";
        }

        return "/img/boards/" + playerColor.name().toLowerCase() + "_" + suffix + ".png";
    }

    private void setAmmo(UserPlayer player) {
        AmmoQuantity ammoQuantity = player.getPlayerBoard().getAmmo();

        for (int i = 0; i < ammoQuantity.getRedAmmo(); ++i) {
            ImageView redAmmo = new ImageView("/img/ammo/redAmmo.png");
            AnchorPane.setLeftAnchor(redAmmo, MapInsetsHelper.firstAmmoInsets.getLeft() + i * MapInsetsHelper.AMMO_HORIZONTAL_OFFSET);
            AnchorPane.setTopAnchor(redAmmo, MapInsetsHelper.firstAmmoInsets.getTop());
            ((AnchorPane) infoPanel.getCenter()).getChildren().add(redAmmo);
        }

        for (int i = 0; i < ammoQuantity.getYellowAmmo(); ++i) {
            ImageView yellowAmmo = new ImageView("/img/ammo/yellowAmmo.png");
            AnchorPane.setLeftAnchor(yellowAmmo, MapInsetsHelper.secondAmmoInsets.getLeft() + i * MapInsetsHelper.AMMO_HORIZONTAL_OFFSET);
            AnchorPane.setTopAnchor(yellowAmmo, MapInsetsHelper.secondAmmoInsets.getTop());
            ((AnchorPane) infoPanel.getCenter()).getChildren().add(yellowAmmo);
        }

        for (int i = 0; i < ammoQuantity.getBlueAmmo(); ++i) {
            ImageView blueAmmo = new ImageView("/img/ammo/blueAmmo.png");
            AnchorPane.setLeftAnchor(blueAmmo, MapInsetsHelper.thirdAmmoInsets.getLeft() + i * MapInsetsHelper.AMMO_HORIZONTAL_OFFSET);
            AnchorPane.setTopAnchor(blueAmmo, MapInsetsHelper.thirdAmmoInsets.getTop());
            ((AnchorPane) infoPanel.getCenter()).getChildren().add(blueAmmo);
        }
    }

    private void setDamages(PlayerBoard playerBoard) {
        List<String> damages = playerBoard.getDamages();

        for (int i = 0; i < damages.size(); ++i) {
            String username = damages.get(i);
            PlayerColor damageDealerColor = guiManager.getPlayerByName(username).getColor();

            ImageView drop = new ImageView(getDropPath(damageDealerColor));
            AnchorPane.setLeftAnchor(drop, MapInsetsHelper.damageInsets.getLeft() + i * MapInsetsHelper.DAMAGE_HORIZONTAL_OFFSET);
            AnchorPane.setTopAnchor(drop, MapInsetsHelper.damageInsets.getTop());

            ((AnchorPane) infoPanel.getCenter()).getChildren().add(drop);
        }
    }

    private void setMarks(PlayerBoard playerBoard) {
        List<String> marks = playerBoard.getMarks();

        for (int i = 0; i < marks.size(); ++i) {
            String username = marks.get(i);
            PlayerColor markDealerColor = guiManager.getPlayerByName(username).getColor();

            ImageView drop = new ImageView(getDropPath(markDealerColor));
            AnchorPane.setLeftAnchor(drop, MapInsetsHelper.marksInsets.getLeft() + i * MapInsetsHelper.MARKS_HORIZONTAL_OFFSET);
            AnchorPane.setTopAnchor(drop, MapInsetsHelper.marksInsets.getTop());

            ((AnchorPane) infoPanel.getCenter()).getChildren().add(drop);
        }
    }

    private void setWeapons(List<WeaponCard> weapons) {
        if (weapons.isEmpty()) {
            return;
        }

        HBox weaponHBox = new HBox();
        weaponHBox.getStyleClass().add("infoHBOX");
        weaponHBox.setAlignment(Pos.BASELINE_CENTER);
        weaponHBox.setSpacing(20);

        for (WeaponCard weapon : weapons) {
            ImageView weaponImage = new ImageView(weapon.getImagePath());

            weaponImage.setFitHeight(WEAPON_CARD_HEIGHT);
            weaponImage.setFitWidth(WEAPON_CARD_WIDTH);

            if (weapon.status() == 1) {
                ColorAdjust monochrome = new ColorAdjust();
                monochrome.setSaturation(-1);
                weaponImage.setEffect(monochrome);
            }

            weaponHBox.getChildren().add(weaponImage);
        }

        AnchorPane.setTopAnchor(weaponHBox, MapInsetsHelper.weaponHBoxInsets.getTop());
        ((AnchorPane) infoPanel.getCenter()).getChildren().add(weaponHBox);
    }

    private void setPowerups(List<PowerupCard> powerups) {
        if (powerups.isEmpty()) {
            return;
        }

        HBox powerupHBox = new HBox();
        powerupHBox.getStyleClass().add("infoHBOX");
        powerupHBox.setAlignment(Pos.BASELINE_CENTER);
        powerupHBox.setSpacing(20);

        for (PowerupCard powerup : powerups) {
            ImageView powerupImage = new ImageView(powerup.getImagePath());
            powerupImage.setFitWidth(POWERUP_CARD_WIDTH);
            powerupImage.setFitHeight(POWERUP_CARD_HEIGHT);
            powerupHBox.getChildren().add(powerupImage);
        }

        AnchorPane.setTopAnchor(powerupHBox, MapInsetsHelper.powerupsHBoxInsets.getTop());
        ((AnchorPane) infoPanel.getCenter()).getChildren().add(powerupHBox);
    }

    private void setPlayerboardSkulls(PlayerBoard playerBoard) {
        Insets startingInsets;
        if (playerBoard.isBoardFlipped()) {
            startingInsets = MapInsetsHelper.playerBoardFrenzySkullInsets;
        } else {
            startingInsets = MapInsetsHelper.playerBoardSkullInsets;
        }

        int skullNum = playerBoard.getSkulls();

        for (int i = 0; i < skullNum; ++i) {
            ImageView skull = new ImageView("/img/skull.png");
            skull.setFitWidth(PLAYER_BOARD_SKULL_WIDTH);
            skull.setFitHeight(PLAYER_BOARD_SKULL_HEIGHT);

            AnchorPane.setLeftAnchor(skull, startingInsets.getLeft() + i * MapInsetsHelper.PLAYER_BOARD_SKULL_HORIZONTAL_OFFSET);
            AnchorPane.setTopAnchor(skull, startingInsets.getTop());

            ((AnchorPane) infoPanel.getCenter()).getChildren().add(skull);
        }
    }

    /**
     * Hides the info panel
     */
    private void hideInfoPanel() {
        infoPanel.getChildren().clear();
        infoPanel.setVisible(false);

        setBoardOpaque(NOT_OPAQUE);
    }

    public void spawn() {
        List<PowerupCard> powerups = guiManager.getPowerups();

        for (int i = 0; i < powerups.size(); i++) {
            ImageView img = new ImageView();
            img.setImage(new Image(powerups.get(i).getImagePath()));
            setPowerupSpawnPosition(img, i, powerups.size());
            img.addEventHandler(MouseEvent.MOUSE_CLICKED, this::onClickPowerupSpawn);
            spawnPanel.getChildren().add(img);
        }

        setBoardOpaque(OPAQUE);
        spawnPanel.setVisible(true);
    }

    private void setPowerupSpawnPosition(ImageView img, int i, int size) {
        double offset = ((i == 0) ? 0 : i * 169d + 20d);

        double x = 0, y;

        switch (size) {
            case 1:
                x = MapInsetsHelper.PADDING_LEFT + 374;
                break;

            case 2:
                if (i != 0) offset = i * 169d + 20d;
                x = MapInsetsHelper.PADDING_LEFT + 195 + offset;
                break;

            case 3:
                x = MapInsetsHelper.PADDING_LEFT + 100.5 + offset;
                break;

            case 4:
                x = MapInsetsHelper.PADDING_LEFT + 6 + offset;
        }

        y = MapInsetsHelper.PADDING_TOP + 215;
        StackPane.setMargin(img, new Insets(x, 0, 0, y));
    }

    private void onClickPowerupSpawn(Event event) {
        ImageView img = (ImageView) event.getTarget();
        PowerupCard powerup = null;

        for (PowerupCard power : guiManager.getPowerups()) {
            String[] split = img.getImage().getUrl().split("/");
            String imgName = split[split.length - 1];

            split = power.getImagePath().split("/");
            String powerImgName = split[split.length - 1];

            if (imgName.equals(powerImgName)) powerup = power;
        }

        try {
            if (!guiManager.sendRequest(MessageBuilder.buildDiscardPowerupRequest(guiManager.getClientToken(), guiManager.getPowerups(), powerup, guiManager.getUsername()))) {
                // TODO: error alert
            }
        } catch (PowerupCardsNotFoundException e) {
            // TODO: error alert
        } finally {
            hideSpawnPanel();
        }
    }

    private String getDropPath(PlayerColor playerColor) {
        return "/img/players/" + playerColor.name().toLowerCase() + "Drop.png";
    }
}
