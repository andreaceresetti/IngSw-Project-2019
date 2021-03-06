package controller;

import enumerations.*;
import exceptions.actions.InvalidActionException;
import exceptions.cards.InvalidPowerupActionException;
import exceptions.cards.WeaponAlreadyChargedException;
import exceptions.cards.WeaponNotChargedException;
import exceptions.game.InvalidGameStateException;
import exceptions.map.InvalidSpawnColorException;
import exceptions.player.EmptyHandException;
import exceptions.player.MaxCardsInHandException;
import exceptions.player.PlayerNotFoundException;
import exceptions.playerboard.NotEnoughAmmoException;
import model.Game;
import model.actions.*;
import model.cards.PowerupCard;

import model.map.Square;
import model.player.Bot;
import model.player.Player;
import model.player.UserPlayer;
import network.message.*;
import utility.GameConstants;
import utility.persistency.SaveGame;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class contains all the methods needed to handle entirely the Round of a player's turn
 */
public class RoundManager {
    private static final String TAGBACK_GRANADE = "TAGBACK GRENADE";
    private static final String TELEPORTER = "TELEPORTER";
    private static final String NEWTON = "NEWTON";
    private static final String TARGETING_SCOPE = "TARGETING SCOPE";

    private Game gameInstance;
    private GameManager gameManager;
    private TurnManager turnManager;

    /**
     * Creates an instance of {@link RoundManager RoundManager} binding to it the {@link GameManager GameManager} and
     * taking also the reference to the {@link Game Game} started
     *
     * @param gameManager the {@link GameManager GameManager} of the started {@link Game Game}
     */
    RoundManager(GameManager gameManager) {
        this.gameInstance = Game.getInstance();
        this.gameManager = gameManager;
    }

    /**
     * Method that inits the {@link TurnManager TurnManager} for the starting {@link Game Game}
     */
    void initTurnManager() {
        this.turnManager = new TurnManager(gameInstance.getPlayers());
    }

    public void initTurnManager(TurnManager otherTurnManager) {
        this.turnManager = new TurnManager(otherTurnManager);
    }

    /**
     * @return the {@link TurnManager TurnManager} for the started {@link Game Game}
     */
    public TurnManager getTurnManager() {
        return this.turnManager;
    }

    /**
     * Method that sets the {@link Action Actions} a {@link UserPlayer UserPLayer} can do at the start of his Turn,
     * depending on the {@link Game Game} state that can be: {@link GameState GameState.NORMAL} or
     * {@link GameState GameState}
     */
    private void setInitialActions() {
        if (gameInstance.getState() == GameState.NORMAL && turnManager.getTurnOwner().getPlayerState() == PossiblePlayerState.FIRST_SPAWN) {
            ActionManager.setStartingPossibleActions(turnManager.getTurnOwner(), gameInstance.isBotPresent());
        } else if (gameInstance.getState() == GameState.NORMAL && turnManager.getTurnOwner().getPlayerState() == PossiblePlayerState.PLAYING) {
            ActionManager.setPossibleActions(turnManager.getTurnOwner());
        }
    }

    /**
     * Method used to set <b>only</b> the {@link ReloadAction ReloadAction} to a {@link UserPlayer UserPlayer} when needed
     */
    private void setReloadAction() {
        turnManager.getTurnOwner().setPossibleActions(EnumSet.of(PossibleAction.RELOAD));
    }

    /**
     * Method that handles the FirstSpawn of the {@link Bot Terminator}, performed by the First
     * {@link UserPlayer Player} that starts the {@link Game Game}
     *
     * @param spawnRequest the {@link BotSpawnRequest TerminatorSpawnRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleTerminatorFirstSpawn(BotSpawnRequest spawnRequest) {
        if (turnManager.getTurnOwner().getPossibleActions().contains(PossibleAction.SPAWN_BOT)) {
            try {
                gameInstance.spawnTerminator(gameInstance.getGameMap().getSpawnSquare(spawnRequest.getSpawnColor()));
            } catch (InvalidSpawnColorException e) {
                return buildNegativeResponse("Invalid color for spawning!");
            }
        } else {
            return buildNegativeResponse("Invalid Action ");
        }

        turnManager.getTurnOwner().changePlayerState(PossiblePlayerState.FIRST_SPAWN);
        return buildPositiveResponse("Terminator has spawned!");
    }

    /**
     * Method that handles the FirstSpawn of a {@link UserPlayer UserPlayer} depending on the {@link PowerupCard PowerupCard}
     * chosen
     *
     * @param discardRequest the {@link DiscardPowerupRequest DiscardPowerupRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleFirstSpawn(DiscardPowerupRequest discardRequest) {
        UserPlayer turnOwner = turnManager.getTurnOwner();
        int firstSpawnPowerup = discardRequest.getPowerup();
        RoomColor spawnColor;

        if (firstSpawnPowerup > turnOwner.getPowerups().length - 1) {
            return buildNegativeResponse("Invalid powerup index  ");
        }

        PowerupCard spawningPowerup = turnOwner.getPowerups()[firstSpawnPowerup];

        if (turnOwner.getPossibleActions().contains(PossibleAction.CHOOSE_SPAWN)) {
            try {
                spawnColor = Ammo.toColor(spawningPowerup.getValue());
                turnOwner.discardPowerupByIndex(discardRequest.getPowerup());
                gameInstance.getPowerupCardsDeck().discardCard(spawningPowerup);
            } catch (EmptyHandException e) {
                // never reached, in first spawn state every player always have two powerups!
                return buildNegativeResponse("GAME ERROR");
            }
        } else {
            return buildNegativeResponse("Invalid Action  ");
        }

        // i spawn the turnOwner and then pass the turn picking the powerups for the next player
        try {
            gameInstance.spawnPlayer(turnOwner, gameInstance.getGameMap().getSpawnSquare(spawnColor));
        } catch (InvalidSpawnColorException e) {
            // never reached, a powerup has always a corresponding spawning color!
        }

        turnOwner.changePlayerState(PossiblePlayerState.PLAYING);

        // actions are set to every player after he has spawned, if terminator is present, first player's turn hasn't the BOT ACTION!
        setInitialActions();
        if (gameInstance.isBotPresent() && turnOwner.isFirstPlayer()) {
            turnOwner.removeAction(PossibleAction.BOT_ACTION);
        }
        return buildPositiveResponse("Player spawned with chosen powerup");
    }

    /**
     * Method that handles the new {@link GameState GameState} in which the {@link Game Game} evolves after the
     * {@link BotAction BotAction} has been performed
     *
     * @param gameState the {@link GameState GameState} in which the {@link GameManager GameManager} needs to evolve
     */
    void afterTerminatorActionHandler(PossibleGameState gameState) {
        if (gameState == PossibleGameState.GAME_STARTED || gameState == PossibleGameState.FINAL_FRENZY || gameState == PossibleGameState.SECOND_ACTION) {
            // if terminator action is done before the 2 actions the game state does not change, otherwise it must be done before passing the turn
            gameManager.changeState(gameState);
            turnManager.getTurnOwner().removeAction(PossibleAction.BOT_ACTION);
        } else if (gameState == PossibleGameState.MISSING_TERMINATOR_ACTION) {
            if (gameInstance.getState().equals(GameState.NORMAL)) {
                setReloadAction();
                gameManager.changeState(PossibleGameState.ACTIONS_DONE);
            } else if (gameInstance.getState().equals(GameState.FINAL_FRENZY)) {
                gameManager.changeState(PossibleGameState.FRENZY_ACTIONS_DONE);
            } else {
                throw new InvalidGameStateException();
            }
        } else {
            throw new InvalidGameStateException();
        }
    }

    /**
     * Method used to Pick two {@link PowerupCard PowerupCards} for a {@link UserPlayer UserPlayer} when spawning
     */
    void pickTwoPowerups() {
        for (int i = 0; i < 2; ++i) {
            PowerupCard drawnPowerup = (PowerupCard) gameInstance.getPowerupCardsDeck().draw();
            try {
                turnManager.getTurnOwner().addPowerup(drawnPowerup);
            } catch (MaxCardsInHandException e) {
                // nothing to do here, never reached when picking for the first time two powerups!
            }
        }
    }

    /**
     * Method that handles the {@link BotAction BotAction}
     *
     * @param terminatorRequest the {@link BotUseRequest UseTerminatorRequest} received
     * @param gameState         the {@link GameState GameState} used by the method
     *                          {@link #afterTerminatorActionHandler(PossibleGameState) afterTerminatorActionHandler}
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleTerminatorAction(BotUseRequest terminatorRequest, PossibleGameState gameState) {
        BotAction botAction;
        UserPlayer botTarget = terminatorRequest.getTargetPlayer() == null ? null : (UserPlayer) gameInstance.getUserPlayerByUsername(terminatorRequest.getTargetPlayer());

        if (turnManager.getTurnOwner().getPossibleActions().contains(PossibleAction.BOT_ACTION)) {
            try {
                botAction = new BotAction(turnManager.getTurnOwner(), botTarget, terminatorRequest.getMovingPosition());
                if (botAction.validate()) {
                    botAction.execute();
                    if (botTarget != null) {
                        turnManager.setDamagedPlayers(new ArrayList<>(List.of((UserPlayer)gameInstance.getUserPlayerByUsername(terminatorRequest.getTargetPlayer()))));
                    }
                } else {
                    return buildNegativeResponse("Terminator action not valid");
                }
            } catch (InvalidActionException | PlayerNotFoundException e) {
                return buildNegativeResponse("Invalid Action ");
            }
        } else {
            return buildNegativeResponse("Player can not do this Action");
        }

        if (!turnManager.getGrenadePossibleUsers().isEmpty()) {
            gameManager.changeState(PossibleGameState.GRANADE_USAGE);
            turnManager.setMarkedByGrenadePlayer(turnManager.getTurnOwner());
            turnManager.setMarkingTerminator(true);
            turnManager.giveTurn(turnManager.getGrenadePossibleUsers().get(0));
            turnManager.resetCount();
            turnManager.setArrivingGameState(gameState);

            return buildGrenadePositiveResponse("Terminator action used, turn is passing to a possible granade user");
        } else {
            afterTerminatorActionHandler(gameState);
        }

        return buildPositiveResponse("Terminator action used");
    }

    /**
     * Method that handles the usage of a TAGBACK GRANADE by a damaged {@link UserPlayer UserPlayer} in the TurnOwner's Round
     *
     * @param granadeMessage the {@link PowerupRequest GranadeRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleGranadeUsage(PowerupRequest granadeMessage) {
        Response tempResponse;

        if (granadeMessage.getPowerup().size() > turnManager.getTurnOwner().getPowerups().length) {
            return buildNegativeResponse("Too many powerups");
        }

        // before marking I save the marks of each player as If a multiple action does not work I can set them back
        List<List<String>> oldMarks = gameInstance.getPlayers().stream().map(player -> player.getPlayerBoard().getMarks()).collect(Collectors.toList());
        if (gameInstance.isBotPresent()) {
            oldMarks.add(gameInstance.getBot().getPlayerBoard().getMarks());
        }

        for (Integer index : granadeMessage.getPowerup()) {
            tempResponse = grenadeUsage(index, granadeMessage);
            if (tempResponse.getStatus() == MessageStatus.ERROR) {
                resetMarks(oldMarks);
                return tempResponse;
            }

            // else everything went right and I can discard the grenades used
        }

        // after having used all the grenades I discard them
        discardPowerups(granadeMessage.getPowerup());

        turnManager.increaseCount();
        // if the player is the last one to use the granade I set back the state to the previous one and give the turn to the next player
        if (turnManager.getTurnCount() > turnManager.getGrenadePossibleUsers().size() - 1) {
            turnManager.giveTurn(turnManager.getMarkedByGrenadePlayer());
            if (turnManager.getMarkingTerminator()) {
                afterTerminatorActionHandler(turnManager.getArrivingGameState());
                return buildPositiveResponse("Grenade used, turn going back to real turn owner");
            }
            gameManager.changeState(handleAfterActionState(turnManager.isSecondAction()));
            return buildPositiveResponse("Grenade used, turn going back to real turn owner");
        }

        // then I give the turn to the next damaged player
        turnManager.giveTurn(turnManager.getGrenadePossibleUsers().get(turnManager.getTurnCount()));

        return buildGrenadePositiveResponse("Grenade has been Used, turn passed to the next possible user");
    }

    /**
     * Handles the usage of the grenade passed as an integer index
     *
     * @param index          the index of the grenade to be used
     * @param granadeMessage the {@link PowerupRequest GranadeRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    private Response grenadeUsage(int index, PowerupRequest granadeMessage) {
        PowerupCard chosenGranade;

        if (index > turnManager.getTurnOwner().getPowerups().length) {
            return buildNegativeResponse("Invalid Powerup Index");
        }

        chosenGranade = turnManager.getTurnOwner().getPowerups()[index];

        if (!chosenGranade.getName().equals(TAGBACK_GRANADE)) {
            return buildNegativeResponse("Invalid Powerup");
        }

        // now I can set the target for the usage of the TAGBACK GRENADE
        if (gameInstance.isBotPresent() && turnManager.getMarkingTerminator()) {
            granadeMessage.setGrenadeTarget(GameConstants.BOT_NAME);
        } else {
            granadeMessage.setGrenadeTarget(turnManager.getMarkedByGrenadePlayer().getUsername());
        }

        try {
            chosenGranade.use(granadeMessage);
        } catch (NotEnoughAmmoException e) {
            // never reached because granade has never a cost
        } catch (InvalidPowerupActionException e) {
            return buildNegativeResponse("Powerup can not be used");
        }

        return new Response("Temp response", MessageStatus.NO_RESPONSE);
    }

    /**
     * Handles the usage of a Scope Request distributing damages correctly on each target
     *
     * @param scopeMessage the {@link PowerupRequest ScopeRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleScopeUsage(PowerupRequest scopeMessage) {
        Response tempResponse;
        int sizeDifference;
        List<Integer> powerupsIndexes = scopeMessage.getPowerup();
        List<Integer> paymentPowerups = new ArrayList<>(scopeMessage.getPaymentPowerups());
        List<String> targets = scopeMessage.getTargetPlayersUsername();

        // checks over every constraint of a SCOPE usage
        if (scopeMessage.getPowerup().isEmpty()) {
            gameManager.changeState(handleAfterActionState(turnManager.isSecondAction()));
            return buildPositiveResponse("Targeting Scope Not Used");
        }

        Response checkResponse = scopeChecks(scopeMessage, powerupsIndexes, paymentPowerups);

        if (checkResponse != null) {
            return checkResponse;
        }

        if (powerupsIndexes.size() < targets.size()) {
            return buildNegativeResponse("Too many Targets");
        } else {
            sizeDifference = powerupsIndexes.size() - targets.size();
        }

        // ammo check
        if (powerupsIndexes.size() - paymentPowerups.size() != 0 && (scopeMessage.getAmmoColor() == null || powerupsIndexes.size() - paymentPowerups.size() != scopeMessage.getAmmoColor().size())) {
            return buildNegativeResponse("Missing Ammo Colors to pay");
        }

        // before going to use a SCOPE I save the damages on each player's board as I can reset them back in case one scope does not work
        List<List<String>> oldDamage = gameInstance.getPlayers().stream().map(player -> player.getPlayerBoard().getDamages()).collect(Collectors.toList());
        if (gameInstance.isBotPresent()) {
            oldDamage.add(gameInstance.getBot().getPlayerBoard().getDamages());
        }

        switch (sizeDifference) {
            case 0:
                tempResponse = oneScopeForEachTarget(scopeMessage);
                break;
            case 1:
                if (powerupsIndexes.size() == 3) {
                    tempResponse = moreScopesForFirstTarget(scopeMessage);
                } else if (powerupsIndexes.size() == 2) {
                    tempResponse = allScopesForOneTarget(scopeMessage, 2);
                } else {
                    tempResponse = new Response("", MessageStatus.ERROR);
                }
                break;
            case 2:
                tempResponse = allScopesForOneTarget(scopeMessage, 3);
                break;
            default:
                return buildNegativeResponse(" Invalid Action ");
        }

        if (tempResponse.getStatus() == MessageStatus.NO_RESPONSE) {
            // after each damaging action I must check id the terminator died. In this case I need
            checkBotAction();

            gameManager.changeState(handleAfterActionState(turnManager.isSecondAction()));
            discardPowerups(Stream.of(powerupsIndexes, paymentPowerups).flatMap(Collection::stream).collect(Collectors.toList()));
            return buildPositiveResponse(tempResponse.getMessage());
        } else {
            resetDamage(oldDamage);
            return tempResponse;
        }
    }

    /**
     * Checks indexes and targets of the scope request
     * @param scopeMessage message of request of scope usage
     * @param powerupsIndexes list of indexes of powerups used
     * @param paymentPowerups list of indexes of pouwerups used for payment
     * @return Returns the invalid response if the check failed, null otherwise
     */
    private Response scopeChecks(PowerupRequest scopeMessage, List<Integer> powerupsIndexes, List<Integer> paymentPowerups) {
        // index Check
        if (!checkScopeIndexes(powerupsIndexes, paymentPowerups)) {
            return buildNegativeResponse("Invalid Indexes in Request");
        }

        // targets Check
        if (!checkScopeTargets(scopeMessage)) {
            return buildNegativeResponse("Invalid Targets in Request");
        }

        return null;
    }

    /**
     * Method that discards the powerups after actions that can be multiple: SCOPE and GRENADE
     *
     * @param discardingIndexes the ArrayList of the indexes of the {@link PowerupCard Powerups} to be discarded
     */
    private void discardPowerups(List<Integer> discardingIndexes) {
        List<Integer> reverseSort = discardingIndexes.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        UserPlayer powerUpper = turnManager.getTurnOwner();

        for (Integer index : reverseSort) {
            try {
                PowerupCard discardingPowerup = powerUpper.getPowerups()[index];
                powerUpper.discardPowerupByIndex(index);
                gameInstance.getPowerupCardsDeck().discardCard(discardingPowerup);
            } catch (EmptyHandException e) {
                // never reached here
            }
        }
    }

    /**
     * Method that sets back the damages of each player if a multiple action did not work
     *
     * @param oldDamage the List of ArrayLists of Strings containing the damages
     */
    private void resetDamage(List<List<String>> oldDamage) {
        if (oldDamage.size() > gameInstance.getPlayers().size()) {
            gameInstance.getBot().getPlayerBoard().setDamages(oldDamage.get(oldDamage.size() - 1));
            oldDamage.remove(oldDamage.size() - 1);
        }

        for (int i = 0; i < oldDamage.size(); ++i) {
            gameInstance.getPlayers().get(i).getPlayerBoard().setDamages(oldDamage.get(i));
        }
    }

    /**
     * Method that sets back the damages of each player if a multiple action did not work
     *
     * @param oldMarks the List of ArrayLists of Strings containing the marks
     */
    private void resetMarks(List<List<String>> oldMarks) {
        if (oldMarks.size() > gameInstance.getPlayers().size()) {
            gameInstance.getBot().getPlayerBoard().setMarks(oldMarks.get(oldMarks.size() - 1));
            oldMarks.remove(oldMarks.size() - 1);
        }

        for (int i = 0; i < oldMarks.size(); ++i) {
            gameInstance.getPlayers().get(i).getPlayerBoard().setMarks(oldMarks.get(i));
        }
    }

    /**
     * Uses one targetingScope on each target
     *
     * @param scopeRequest the {@link PowerupRequest PowerupRequest} received
     * @return positive or negative {@link Response Response} handled by the server
     */
    private Response oneScopeForEachTarget(PowerupRequest scopeRequest) {
        PowerupRequest tempRequest;
        UserPlayer turnOwner = turnManager.getTurnOwner();
        List<Integer> paymentPowerups = scopeRequest.getPaymentPowerups();
        List<Ammo> ammoColors = scopeRequest.getAmmoColor();

        for (int i = 0; i < scopeRequest.getPowerup().size(); ++i) {
            if (!paymentPowerups.isEmpty()) {
                tempRequest = new PowerupRequest.PowerupRequestBuilder(scopeRequest.getSenderUsername(), scopeRequest.getToken(), new ArrayList<>(List.of(scopeRequest.getPowerup().get(i))))
                        .paymentPowerups(new ArrayList<>(List.of(paymentPowerups.get(0))))
                        .targetPlayersUsername(new ArrayList<>(List.of(scopeRequest.getTargetPlayersUsername().get(i))))
                        .build();
                paymentPowerups.remove(0);
            } else {
                tempRequest = new PowerupRequest.PowerupRequestBuilder(scopeRequest.getSenderUsername(), scopeRequest.getToken(), new ArrayList<>(List.of(scopeRequest.getPowerup().get(i))))
                        .targetPlayersUsername(new ArrayList<>(List.of(scopeRequest.getTargetPlayersUsername().get(i))))
                        .ammoColor(new ArrayList<>(List.of(ammoColors.get(0))))
                        .build();
                ammoColors.remove(0);
            }
            try {
                turnOwner.getPowerups()[scopeRequest.getPowerup().get(i)].use(tempRequest);
            } catch (NotEnoughAmmoException e) {
                return buildNegativeResponse("Not Enough Ammo");
            } catch (InvalidPowerupActionException e) {
                return buildNegativeResponse(" Invalid Action");
            }
        }

        return new Response("Targeting Scope/s used ", MessageStatus.NO_RESPONSE);
    }

    /**
     * Uses two targeting scopes on first target and one on the second
     *
     * @param scopeRequest the {@link PowerupRequest PowerupRequest} received
     * @return positive or negative {@link Response Response} handled by the server
     */
    private Response moreScopesForFirstTarget(PowerupRequest scopeRequest) {
        PowerupRequest tempRequest;
        UserPlayer turnOwner = turnManager.getTurnOwner();
        List<Ammo> ammoColors = scopeRequest.getAmmoColor();

        for (int i = 0; i < 2; ++i) {
            // impossible that there are payment powerups in this case!
            tempRequest = new PowerupRequest.PowerupRequestBuilder(scopeRequest.getSenderUsername(), scopeRequest.getToken(), new ArrayList<>(List.of(scopeRequest.getPowerup().get(i))))
                    .targetPlayersUsername(new ArrayList<>(List.of(scopeRequest.getTargetPlayersUsername().get(0))))
                    .ammoColor(new ArrayList<>(List.of(ammoColors.get(0))))
                    .build();
            ammoColors.remove(0);

            try {
                turnOwner.getPowerups()[scopeRequest.getPowerup().get(i)].use(tempRequest);
            } catch (NotEnoughAmmoException e) {
                return buildNegativeResponse("Not Enough Ammo");
            } catch (InvalidPowerupActionException e) {
                return buildNegativeResponse(" Invalid Action");
            }
        }

        tempRequest = new PowerupRequest.PowerupRequestBuilder(scopeRequest.getSenderUsername(), scopeRequest.getToken(), new ArrayList<>(List.of(scopeRequest.getPowerup().get(2))))
                .targetPlayersUsername(new ArrayList<>(List.of(scopeRequest.getTargetPlayersUsername().get(1))))
                .ammoColor(new ArrayList<>(List.of(ammoColors.get(0))))
                .build();
        try {
            turnOwner.getPowerups()[2].use(tempRequest);
        } catch (NotEnoughAmmoException e) {
            return buildNegativeResponse("Not Enough Ammo ");
        } catch (InvalidPowerupActionException e) {
            return buildNegativeResponse("Invalid Action  ");
        }

        return new Response("Targeting Scope/s used", MessageStatus.NO_RESPONSE);
    }

    /**
     * Uses all targeting scopes on the same target
     *
     * @param scopeRequest   the {@link PowerupRequest PowerupRequest} received
     * @param numberOfScopes number of targeting scopes to be used
     * @return positive or negative {@link Response Response} handled by the server
     */
    private Response allScopesForOneTarget(PowerupRequest scopeRequest, int numberOfScopes) {
        PowerupRequest tempRequest;
        UserPlayer turnOwner = turnManager.getTurnOwner();
        List<Integer> paymentPowerups = scopeRequest.getPaymentPowerups();
        List<Ammo> ammoColors = scopeRequest.getAmmoColor();

        for (int i = 0; i < numberOfScopes; ++i) {
            if (!paymentPowerups.isEmpty()) {
                tempRequest = new PowerupRequest.PowerupRequestBuilder(scopeRequest.getSenderUsername(), scopeRequest.getToken(), new ArrayList<>(List.of(scopeRequest.getPowerup().get(i))))
                        .paymentPowerups(scopeRequest.getPaymentPowerups())
                        .targetPlayersUsername(new ArrayList<>(List.of(scopeRequest.getTargetPlayersUsername().get(0))))
                        .build();
                paymentPowerups.remove(0);
            } else {
                tempRequest = new PowerupRequest.PowerupRequestBuilder(scopeRequest.getSenderUsername(), scopeRequest.getToken(), new ArrayList<>(List.of(scopeRequest.getPowerup().get(i))))
                        .ammoColor(new ArrayList<>(List.of(ammoColors.get(0))))
                        .targetPlayersUsername(new ArrayList<>(List.of(scopeRequest.getTargetPlayersUsername().get(0))))
                        .build();
                ammoColors.remove(0);
            }
            try {
                turnOwner.getPowerups()[scopeRequest.getPowerup().get(i)].use(tempRequest);
            } catch (NotEnoughAmmoException e) {
                return buildNegativeResponse("Not Enough Ammo ");
            } catch (InvalidPowerupActionException e) {
                return buildNegativeResponse("Invalid  Action");
            }
        }

        return new Response("Targeting Scope/s used", MessageStatus.NO_RESPONSE);
    }

    /**
     * Validates all the indexes and dimensions in the Powerups Lists
     *
     * @param powerupsIndexes List of using SCOPEs
     * @param paymentPowerups List of payment Powerups
     * @return true if every check is consistent, otherwise false
     */
    private boolean checkScopeIndexes(List<Integer> powerupsIndexes, List<Integer> paymentPowerups) {
        UserPlayer turnOwner = turnManager.getTurnOwner();

        if (powerupsIndexes.size() < paymentPowerups.size()) {
            return false;
        }

        for (Integer index : powerupsIndexes) {
            for (Integer paymentIndex : paymentPowerups) {
                if (index.equals(paymentIndex)) {
                    return false;
                }
            }
        }

        if (powerupsIndexes.size() > turnOwner.getPowerups().length) {
            return false;
        }

        for (Integer index : powerupsIndexes) {
            if (!turnOwner.getPowerups()[index].getName().equals(TARGETING_SCOPE)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks targets constraints given on the previous {@link ShootAction ShootAction}
     *
     * @param scopeMessage the Message received
     * @return true
     */
    private boolean checkScopeTargets(PowerupRequest scopeMessage) {
        List<String> targetsUsernames = scopeMessage.getTargetPlayersUsername();
        boolean targetPresent = true;

        if (targetsUsernames.isEmpty() && !scopeMessage.getPowerup().isEmpty()) {
            return false;
        }

        for (int i = 0; i < targetsUsernames.size() && targetPresent; ++i) {
            for (Player damagedPlayers : turnManager.getDamagedPlayers()) {
                if (targetsUsernames.get(i).equals(damagedPlayers.getUsername())) {
                    targetPresent = true;
                    break;
                }

                targetPresent = false;
            }
        }

        return targetPresent;
    }

    /**
     * Method used to handle the usage of one of the powerups: NEWTON or TELEPORTER
     *
     * @param powerupRequest the {@link PowerupRequest PowerupRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handlePowerupAction(PowerupRequest powerupRequest) {
        PowerupCard chosenPowerup;

        if (powerupRequest.getPowerup().size() != 1) {
            return buildNegativeResponse("Too many powerups!");
        }

        if (powerupRequest.getPowerup().get(0) < 0 || powerupRequest.getPowerup().get(0) > turnManager.getTurnOwner().getPowerups().length - 1) {
            return buildNegativeResponse("Invalid Powerup index!");
        }

        chosenPowerup = turnManager.getTurnOwner().getPowerups()[powerupRequest.getPowerup().get(0)];

        if (!chosenPowerup.getName().equals(NEWTON) && !chosenPowerup.getName().equals(TELEPORTER)) {
            return buildNegativeResponse("Invalid Powerup");
        }

        try {
            chosenPowerup.use(powerupRequest);
        } catch (NotEnoughAmmoException e) {
            // never reached because neither newton nor teleporter need a cost
        } catch (InvalidPowerupActionException e) {
            return buildNegativeResponse("Powerup can not be used");
        }

        // after having used the powerup I discard it from the players hand
        try {
            turnManager.getTurnOwner().discardPowerupByIndex(powerupRequest.getPowerup().get(0));
            gameInstance.getPowerupCardsDeck().discardCard(chosenPowerup);
        } catch (EmptyHandException e) {
            // never reached if the player has the powerup!
        }

        return buildPositiveResponse("Powerup has been used");
    }

    /**
     * Method that handles the {@link MoveAction MoveAction} performed by the TurnOwner
     *
     * @param moveRequest  the {@link MoveRequest MoveRequest} received
     * @param secondAction Boolean that specifies if the performing action is the second
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleMoveAction(MoveRequest moveRequest, boolean secondAction) {
        UserPlayer turnOwner = turnManager.getTurnOwner();
        PossibleAction actionType;
        MoveAction moveAction;

        if (turnOwner.getPossibleActions().contains(PossibleAction.MOVE)) {
            actionType = PossibleAction.MOVE;
        } else if (turnOwner.getPossibleActions().contains(PossibleAction.FRENZY_MOVE)) {
            actionType = PossibleAction.FRENZY_MOVE;
        } else {
            return buildNegativeResponse("Player can not do this action ");
        }

        // first I build the MoveAction
        moveAction = new MoveAction(turnOwner, moveRequest.getSenderMovePosition(), actionType);

        try {
            if (moveAction.validate()) {
                moveAction.execute();
            } else {
                return buildNegativeResponse("Invalid Move action");
            }
        } catch (InvalidActionException e) {
            return buildNegativeResponse("Invalid Move action");
        }

        gameManager.changeState(handleAfterActionState(secondAction));
        return buildPositiveResponse("Move action done");
    }

    /**
     * Method thah handles a {@link PickAction PickAction} performed by the TurnOwner
     *
     * @param pickRequest  the {@link MovePickRequest PickRequest} received
     * @param secondAction Boolean that specifies if the performing action is the second
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handlePickAction(MovePickRequest pickRequest, boolean secondAction) {
        UserPlayer turnOwner = turnManager.getTurnOwner();
        PickAction pickAction;
        Set<PossibleAction> ownersActions = turnOwner.getPossibleActions();
        PossibleAction actionType;
        Square movingSquare;

        if (ownersActions.contains(PossibleAction.MOVE_AND_PICK)) {
            actionType = PossibleAction.MOVE_AND_PICK;
        } else if (ownersActions.contains(PossibleAction.ADRENALINE_PICK)) {
            actionType = PossibleAction.ADRENALINE_PICK;
        } else if (ownersActions.contains(PossibleAction.FRENZY_PICK)) {
            actionType = PossibleAction.FRENZY_PICK;
        } else if (ownersActions.contains(PossibleAction.LIGHT_FRENZY_PICK)) {
            actionType = PossibleAction.LIGHT_FRENZY_PICK;
        } else {
            return buildNegativeResponse("Player can not do this action");
        }

        // first I understand if the moving square is a spawn or a tile one then I build the relative pick action
        movingSquare = gameInstance.getGameMap().getSquare(pickRequest.getSenderMovePosition());

        if (movingSquare.getSquareType() == SquareType.TILE) {
            pickAction = new PickAction(turnOwner, actionType, pickRequest);
        } else if (movingSquare.getSquareType() == SquareType.SPAWN) {
            pickAction = new PickAction(turnOwner, actionType, pickRequest);
        } else {
            throw new NullPointerException("A square must always have a type!");
        }

        // now I can try to validate and use the action
        try {
            if (pickAction.validate()) {
                pickAction.execute();
            } else {
                return buildNegativeResponse("Invalid Pick Action");
            }
        } catch (InvalidActionException e) {
            return buildNegativeResponse("Invalid Pick Action");
        }

        gameManager.changeState(handleAfterActionState(secondAction));
        return buildPositiveResponse("Pick Action done");
    }

    /**
     * Method that handles a {@link ShootAction ShootAction} performed by the TurnOwner
     *
     * @param shootRequest the {@link ShootRequest ShootRequest} received before
     * @param secondAction Boolean that specifies if the performing action is the second
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleShootAction(ShootRequest shootRequest, boolean secondAction) {
        UserPlayer turnOwner = turnManager.getTurnOwner();
        ShootAction shootAction;
        PossibleAction actionType;
        Set<PossibleAction> ownersActions = turnOwner.getPossibleActions();
        List<Integer> beforeShootPlayers;

        if (ownersActions.contains(PossibleAction.SHOOT)) {
            actionType = PossibleAction.SHOOT;
        } else if (ownersActions.contains(PossibleAction.ADRENALINE_SHOOT)) {
            actionType = PossibleAction.ADRENALINE_SHOOT;
        } else if (ownersActions.contains(PossibleAction.FRENZY_SHOOT)) {
            actionType = PossibleAction.FRENZY_SHOOT;
        } else if (ownersActions.contains(PossibleAction.LIGHT_FRENZY_SHOOT)) {
            actionType = PossibleAction.LIGHT_FRENZY_SHOOT;
        } else {
            return buildNegativeResponse("Player can not do this action");
        }

        // first I can build the shoot action
        shootAction = new ShootAction(turnOwner, actionType, shootRequest);

        // now I can try to validate and use the action, care, a shoot action can throw different exceptions, each will be returned with a different response
        try {
            if (shootAction.validate()) {
                beforeShootPlayers = new ArrayList<>(gameInstance.getPlayersDamage());
                shootAction.execute();
                turnManager.setDamagedPlayers(buildDamagedPlayers(beforeShootPlayers));
            } else {
                return buildNegativeResponse("Invalid Shoot Action");
            }
        } catch (WeaponAlreadyChargedException e) {
            return buildNegativeResponse("Trying to recharge an already charged weapon with frenzy shoot");
        } catch (NotEnoughAmmoException e) {
            return buildNegativeResponse("Not enough ammo");
        } catch (WeaponNotChargedException e) {
            return buildNegativeResponse("Not charged weapon can not be used to shoot");
        } catch (InvalidActionException e) {
            return buildNegativeResponse("Invalid Shoot Action");
        }

        checkBotAction();

        if (checkScopeUsage(turnOwner)) {
            turnManager.setSecondAction(secondAction);
            gameManager.changeState(PossibleGameState.SCOPE_USAGE);

            return buildScopePositiveResponse();
        } else if (!turnManager.getGrenadePossibleUsers().isEmpty()) {
            gameManager.changeState(PossibleGameState.GRANADE_USAGE);
            turnManager.setMarkedByGrenadePlayer(turnManager.getTurnOwner());
            turnManager.setMarkingTerminator(false);
            turnManager.giveTurn(turnManager.getGrenadePossibleUsers().get(0));
            turnManager.resetCount();
            turnManager.setSecondAction(secondAction);

            return buildGrenadePositiveResponse("Shoot Action done, turn is passing to a possible granade user");
        } else {
            gameManager.changeState(handleAfterActionState(secondAction));
        }

        return buildPositiveResponse("Shoot Action done");
    }

    /**
     * Method that handles the {@link ReloadAction ReloadAction} performed by the TurnOwner
     *
     * @param reloadRequest the {@link ReloadRequest ReloadRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleReloadAction(ReloadRequest reloadRequest) {
        UserPlayer turnOwner = turnManager.getTurnOwner();
        ReloadAction reloadAction;

        if (reloadRequest.getWeapons().size() > turnOwner.getWeapons().length) {
            return buildNegativeResponse("Too many weapons");
        }

        if (turnOwner.getPossibleActions().contains(PossibleAction.RELOAD)) {
            reloadAction = new ReloadAction(turnOwner, reloadRequest);
        } else {
            return buildNegativeResponse("Invalid Action");
        }

        try {
            if (reloadAction.validate()) {
                reloadAction.execute();
            } else {
                return buildNegativeResponse("Invalid Action");
            }
        } catch (WeaponAlreadyChargedException e) {
            return buildNegativeResponse("You are trying to recharge a weapon that is already charged");
        } catch (NotEnoughAmmoException e) {
            return buildNegativeResponse("Not enough ammo to reload");
        } catch (InvalidActionException e) {
            return buildNegativeResponse("Incorrect Payment powerups indexes");
        }

        // after a reload action a player always passes his turn and the game has to manage the death players
        return deathPlayersHandler(PossibleGameState.RELOAD_PASS);
    }

    /**
     * Method that builds an ArrayList of the damaged {@link UserPlayer UserPlayers} after a {@link ShootAction ShootAction},
     * used by the {@link #handleScopeUsage(PowerupRequest) handleScopeUsage} method to verify that the TARGETING SCOPE
     * is used only on a damaged {@link UserPlayer UserPlayer}
     *
     * @param beforeShootDamage the List of damage before the {@link ShootAction ShootAction}
     * @return the ArrayList of damaged {@link UserPlayer UserPlayers}
     */
    private ArrayList<Player> buildDamagedPlayers(List<Integer> beforeShootDamage) {
        ArrayList<Player> reallyDamagedPlayers = new ArrayList<>();

        for (int i = 0; i < gameInstance.getPlayers().size(); ++i) {
            if (gameInstance.getPlayers().get(i).getPlayerBoard().getDamageCount() > beforeShootDamage.get(i)) {
                reallyDamagedPlayers.add(gameInstance.getPlayers().get(i));
            }
        }

        if (beforeShootDamage.size() > gameInstance.getPlayers().size() && gameInstance.isBotPresent()) {
            reallyDamagedPlayers.add(gameInstance.getBot());
        }

        return reallyDamagedPlayers;
    }

    /**
     * Method that handles the Phase of passing an action, handling in case the Dead {@link UserPlayer UserPlayers} giving
     * them a turn to Respawn in the method {@link #deathPlayersHandler(PossibleGameState) deathPlayersHandler}
     *
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handlePassAction() {
        if (gameInstance.getState() == GameState.NORMAL) {
            return deathPlayersHandler(PossibleGameState.PASS_NORMAL_TURN);
        } else if (gameInstance.getState() == GameState.FINAL_FRENZY) {
            Response tempResponse;

            if (turnManager.getTurnOwner().equals(turnManager.getLastPlayer())) {
                // if reached, game has ended, last remaining points are calculated and a winner is declared!
                gameManager.endGame();

                // game is not saved before the game ends as it can be played with the last save done
                gameManager.sendPrivateUpdates();
                return new Response("Turn passed and GAME HAS ENDED", MessageStatus.OK);
            }

            tempResponse = deathPlayersHandler(PossibleGameState.PASS_FRENZY_TURN);
            if(tempResponse.getStatus() == MessageStatus.OK) {
                gameManager.sendPrivateUpdates();
                return tempResponse;
            } else {
                throw new InvalidGameStateException();
            }
        } else {
            throw new InvalidGameStateException();
        }
    }

    /**
     * Method that handles the Death of the {@link model.player.Player Players} that died after the end of each Turn.
     * In case the dead {@link UserPlayer UserPlayers} are more than one, the {@link TurnManager TurnManager} handles
     * their Turns with some parameters set in this method
     *
     * @param nextPassState the {@link PossibleGameState PossibleGameState} in which the {@link GameManager GameManager}
     *                      evolves after every dead {@link UserPlayer Player} is respawned
     * @return a positive or negative {@link Response Response} handled by the server
     */
    private Response deathPlayersHandler(PossibleGameState nextPassState) {
        List<UserPlayer> deathPlayers = gameInstance.getDeathPlayers();

        if (gameInstance.isBotPresent() && gameInstance.getBot().getPlayerBoard().getDamageCount() > 10) {
            // first of all I control if the current player has done a double kill
            if (!gameInstance.getDeathPlayers().isEmpty()) {
                turnManager.getTurnOwner().addPoints(1);
            }

            turnManager.setFrenzyActivator();
            gameInstance.getBot().setPosition(null);
            turnManager.setArrivingGameState(nextPassState);
            gameManager.changeState(PossibleGameState.TERMINATOR_RESPAWN);

            if(nextPassState == PossibleGameState.RELOAD_PASS) {
                return buildPositiveResponse("Reload action done, respawn terminator now");
            } else {
                return buildPositiveResponse("Terminator has died respawn him before passing");
            }
        } else if (!deathPlayers.isEmpty()) {
            // first of all I control if the current player has done a double kill
            if (deathPlayers.size() > 1) {
                turnManager.getTurnOwner().addPoints(1);
            }

            // there are death players I set everything I need to respawn them
            turnManager.setFrenzyActivator();
            turnManager.setDeathPlayers(deathPlayers);
            gameManager.changeState(PossibleGameState.MANAGE_DEATHS);
            turnManager.giveTurn(deathPlayers.get(0));
            turnManager.getTurnOwner().setSpawningCard(drawPowerup());
            turnManager.resetCount();
            turnManager.setArrivingGameState(nextPassState);

            if(nextPassState == PossibleGameState.RELOAD_PASS) {
                return buildPositiveResponse("Reload action done, death players need to respawn");
            } else {
                return buildPositiveResponse("Turn passed");
            }
        } else {
            // if no players have died the GameState remains the same
            return handleNextTurn(nextPassState);
        }
    }

    /**
     * Method that handles the Respawn of the {@link Bot Terminatore} performed by the TurnOwner
     *
     * @param respawnRequest the {@link BotSpawnRequest TerminatorRespawnRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleTerminatorRespawn(BotSpawnRequest respawnRequest) {
        List<UserPlayer> deathPlayers = gameInstance.getDeathPlayers();

        try {
            gameInstance.spawnTerminator(gameInstance.getGameMap().getSpawnSquare(respawnRequest.getSpawnColor()));
        } catch (InvalidSpawnColorException e) {
            return buildNegativeResponse("Invalid Color for Spawning");
        }

        if (!gameInstance.getDeathPlayers().isEmpty()) {
            // there are death players: I set everything I need to respawn them
            turnManager.setDeathPlayers(deathPlayers);
            gameManager.changeState(PossibleGameState.MANAGE_DEATHS);
            turnManager.giveTurn(deathPlayers.get(0));
            deathPlayers.get(0).setSpawningCard(drawPowerup());
            turnManager.resetCount();
            return buildPositiveResponse("Turn passed after Spawning the Terminator");
        } else {
            if (gameInstance.remainingSkulls() == 1) {   // last skull is going to be removed, frenzy mode has to be activated
                turnManager.setFrenzyPlayers();
                turnManager.setLastPlayer();
                return handleNextTurn(PossibleGameState.PASS_FRENZY_TURN);
            } else {
                return handleNextTurn(PossibleGameState.PASS_NORMAL_BOT_TURN);
            }
        }
    }

    /**
     * Method that handles the Respawn of a dead {@link UserPlayer UserPlayer}
     *
     * @param respawnRequest the {@link DiscardPowerupRequest RespawnRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handlePlayerRespawn(DiscardPowerupRequest respawnRequest) {
        UserPlayer turnOwner = turnManager.getTurnOwner();
        PowerupCard spawnPowerup;
        RoomColor spawnColor;
        int powerupIndex = respawnRequest.getPowerup();

        // if powerupIndex is 3 means that the player wants to respawn with the drawn powerup, otherwise with the specified one
        if (powerupIndex < 0 || powerupIndex > 3) {
            return buildNegativeResponse("Invalid powerup index");
        }

        if (powerupIndex != 3 && powerupIndex > turnOwner.getPowerups().length - 1) {
            return buildNegativeResponse("Invalid powerup index");
        }

        if (powerupIndex == 3) {
            spawnPowerup = turnOwner.getSpawningCard();
            gameInstance.getPowerupCardsDeck().discardCard(spawnPowerup);
        } else {
            spawnPowerup = turnOwner.getPowerups()[powerupIndex];
            gameInstance.getPowerupCardsDeck().discardCard(spawnPowerup);
            try {
                turnOwner.discardPowerup(spawnPowerup);
                turnOwner.addPowerup(turnOwner.getSpawningCard());
            } catch (MaxCardsInHandException | EmptyHandException e) {
                // never happen at this point
            }
        }

        turnOwner.setSpawningCard(null);
        spawnColor = Ammo.toColor(spawnPowerup.getValue());

        // now that I know the color of the spawning square I can respawn the player and set his initial actions
        try {
            gameInstance.spawnPlayer(turnOwner, gameInstance.getGameMap().getSpawnSquare(spawnColor));
            setInitialActions();
        } catch (InvalidSpawnColorException e) {
            // never reached, a powerup has always a corresponding spawning color!
        }

        // now I have to pass the turn to the next death player and pick a card for him
        turnManager.increaseCount();
        if (turnManager.getTurnCount() > turnManager.getDeathPlayers().size() - 1) {
            if ((turnManager.getDeathPlayers().size() == 1 && gameInstance.remainingSkulls() == 1) || gameInstance.remainingSkulls() == 0) {   // last skull is going to be removed, frenzy mode has to be activated
                turnManager.setFrenzyPlayers();
                turnManager.setLastPlayer();
                return handleNextTurn(PossibleGameState.PASS_FRENZY_TURN);
            } else {
                return handleNextTurn(PossibleGameState.PASS_NORMAL_TURN);
            }
        }

        // otherwise there are still death players and the next one has to respawn
        turnManager.giveTurn(turnManager.getDeathPlayers().get(turnManager.getTurnCount()));
        turnManager.getTurnOwner().setSpawningCard(drawPowerup());

        return buildPositiveResponse("Player Respawned");
    }

    /**
     * This is the real method that changes the State of the {@link GameManager GameManager} after each {@link Action Action}
     * is performed. In this method is used the method {@link #setReloadAction() setReloadAction} as the TurnOwner's
     * {@link ReloadAction ReloadAction} can be handled
     *
     * @param secondAction Boolean that specifies if the performing action is the second
     * @return the {@link PossibleGameState PossibleGameState} in which the {@link GameManager GameManager} has to evolve
     */
    PossibleGameState handleAfterActionState(boolean secondAction) {
        if (!secondAction) {
            return PossibleGameState.SECOND_ACTION;
        } else {
            if (turnManager.getTurnOwner().getPossibleActions().contains(PossibleAction.BOT_ACTION)) {
                return PossibleGameState.MISSING_TERMINATOR_ACTION;
            } else {
                if (gameInstance.getState().equals(GameState.NORMAL)) {
                    setReloadAction();
                    return PossibleGameState.ACTIONS_DONE;
                } else if (gameInstance.getState().equals(GameState.FINAL_FRENZY)) {
                    return PossibleGameState.FRENZY_ACTIONS_DONE;
                } else {
                    throw new InvalidGameStateException();
                }
            }
        }
    }

    /**
     * This is the real method that Passes the Turn from the TurnOwner to the next one
     *
     * @param arrivingState the {@link PossibleGameState PossibleGameState} of the {@link GameManager GameManager} used
     *                      to handle the new State in which the {@link GameManager GameManager} is evolving
     * @return a positive or negative {@link Response Response} handled by the server
     */
    private Response handleNextTurn(PossibleGameState arrivingState) {
        do {
            // first I set the turn to the next player and give him his possible actions
            turnManager.nextTurn();

            // if it is the first turn of the last player I set the first turn to false as no more powerups would be picked
            if (turnManager.isFirstTurn() && turnManager.endOfRound()) {
                turnManager.endOfFirstTurn();
                pickTwoPowerups();
            }

            // then if I am in the very first round of the game I also need to pick the two powerups for the next spawning player
            if (turnManager.isFirstTurn()) {
                pickTwoPowerups();
            }

        } while (turnManager.getTurnOwner().getPlayerState() == PossiblePlayerState.DISCONNECTED);

        /*  Initial actions are set only for the next connected Player, disconneted ones in fact when reconnected may have different
            actions if the game state changed while they where not connected. These actions will be set as for all connected players
            thanks for the reconnection
         */
        if(gameInstance.getState() == GameState.NORMAL) {
            setInitialActions();
        }

        // then I reset the missing cards on the board
        gameInstance.getGameMap().addMissingCards();

        if (arrivingState == PossibleGameState.PASS_NORMAL_TURN) {
            gameManager.changeState(PossibleGameState.GAME_STARTED);
            return buildPositiveResponse("Turn Passed");
        } else if (arrivingState == PossibleGameState.RELOAD_PASS) {
            gameManager.changeState(PossibleGameState.GAME_STARTED);
            return buildPositiveResponse("Reload action done and turn passed");
        } else if (arrivingState == PossibleGameState.PASS_FRENZY_TURN) {
            gameManager.changeState(PossibleGameState.FINAL_FRENZY);
            SaveGame.saveGame(gameManager);
            return new Response("Turn Passed and Frenzy Starting", MessageStatus.OK);
        } else if (arrivingState == PossibleGameState.PASS_NORMAL_BOT_TURN) {
            gameManager.changeState(PossibleGameState.GAME_STARTED);
            SaveGame.saveGame(gameManager);
            return new Response("Turn Passed after bot action", MessageStatus.OK);
        } else {
            throw new InvalidGameStateException();
        }
    }

    /**
     * Method that simply checks if the TurnOwner has a SCOPE in his hand
     *
     * @param turnOwner the {@link UserPlayer UserPlayer} to be checked
     * @return true if the shooter has a SCOPE, otherwis false
     */
    private boolean checkScopeUsage(UserPlayer turnOwner) {
        for (PowerupCard powerupCard : turnOwner.getPowerups()) {
            if (powerupCard.getName().equals(TARGETING_SCOPE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Method that handles the spawn of a {@link UserPlayer UserPlayer} while he disconnects from the game on the very
     * first round. If the {@link Bot Bot} is present his spawn is also managed
     *
     * @param spawnPlayer     if true the {@link UserPlayer UserPlayer} hasn't spawned yet
     * @param spawnTerminator if true the {@link Bot Bot} hasn't spawned yet
     */
    void handleRandomSpawn(boolean spawnPlayer, boolean spawnTerminator) {
        int randomIndex = Game.rand.nextInt(1);

        PowerupCard spawningPowerup = getTurnManager().getTurnOwner().getPowerups()[randomIndex];
        RoomColor spawnColor = null;

        if (spawnTerminator && getTurnManager().getTurnOwner().getPossibleActions().contains(PossibleAction.SPAWN_BOT)) {
            try {
                gameInstance.spawnTerminator(gameInstance.getGameMap().getSpawnSquare(RoomColor.getRandomSpawnColor()));
            } catch (InvalidSpawnColorException e) {
                // never reached random color is always a correct color
            }
        }

        if (spawnPlayer && getTurnManager().getTurnOwner().getPossibleActions().contains(PossibleAction.CHOOSE_SPAWN)) {
            try {
                spawnColor = Ammo.toColor(spawningPowerup.getValue());
                getTurnManager().getTurnOwner().discardPowerupByIndex(randomIndex);
            } catch (EmptyHandException e) {
                // never reached, in first spawn state every player always have two powerups!
            }

            try {
                gameInstance.spawnPlayer(getTurnManager().getTurnOwner(), gameInstance.getGameMap().getSpawnSquare(spawnColor));
            } catch (InvalidSpawnColorException e) {
                // never reached, a powerup has always a corresponding spawning color!
            }

            getTurnManager().getTurnOwner().changePlayerState(PossiblePlayerState.PLAYING);
            setInitialActions();
        }
    }

    /**
     * Checks if the therminator has died after a shoot or scope action. In this case the BOT_ACTION can not be used!
     */
    private void checkBotAction() {
        if(gameInstance.isBotPresent() && gameInstance.getBot().getPlayerBoard().getDamageCount() > 10) {
            turnManager.getTurnOwner().removeAction(PossibleAction.BOT_ACTION);
        }
    }

    /**
     * @return always a {@link PowerupCard Powerup}, in case the deck is finished a new one is flushed with the already
     * used powerups
     */
    private PowerupCard drawPowerup() {
        PowerupCard drawnPowerup = (PowerupCard) gameInstance.getPowerupCardsDeck().draw();
        if(drawnPowerup == null) {
            gameInstance.getPowerupCardsDeck().flush();
            drawnPowerup = (PowerupCard) gameInstance.getPowerupCardsDeck().draw();
        }

        return drawnPowerup;
    }

    /**
     * Method that builds a Positive {@link Response Response}, that has {@link MessageStatus MessageStatus.OK}
     * For real this method is the most important one of this Class, infact, it also:
     * (i) send the new Game status to each player
     * (ii) saves the Game status to have persistency
     *
     * @param reason the reason why the {@link Response Response} is Positive
     * @return the Positive {@link Response Response} built
     */
    private Response buildPositiveResponse(String reason) {
        gameManager.sendPrivateUpdates();
        SaveGame.saveGame(gameManager);
        return new Response(reason, MessageStatus.OK);
    }

    /**
     * Method that builds a Positive {@link Response Response} everyTime the turn has to be assigned to a
     * {@link UserPlayer UserPlayer} that can use a TAGBACK GRENADE to mark the shootingPlayer that is
     * damaging him
     *
     * @param reason the reason why the {@link Response Response} is Positive
     * @return the Positive {@link Response Response} built
     */
    private Response buildGrenadePositiveResponse(String reason) {
        gameManager.sendGrenadePrivateUpdates();
        SaveGame.saveGame(gameManager);
        return new Response(reason, MessageStatus.OK);
    }

    /**
     * Method that builds a {@link MessageStatus NEED_PLAYER_ACTION} {@link Response Response} after a targeting
     * scope can be used by a shooting {@link UserPlayer UserPlayer}
     *
     * @return the {@link MessageStatus NEED_PLAYER_ACTION} {@link Response Response} built
     */
    private Response buildScopePositiveResponse() {
        gameManager.sendPrivateUpdates();
        SaveGame.saveGame(gameManager);
        return new Response("Shoot Action done, shooter can use a Scope", MessageStatus.NEED_PLAYER_ACTION);
    }

    /**
     * Method that builds a Negative {@link Response Response}, that has {@link MessageStatus MessageStatus.ERROR}
     *
     * @param reason the reason why the {@link Response Response} is Negative
     * @return the Negative {@link Response Response} built
     */
    private Response buildNegativeResponse(String reason) {
        return new Response(reason, MessageStatus.ERROR);
    }
}
