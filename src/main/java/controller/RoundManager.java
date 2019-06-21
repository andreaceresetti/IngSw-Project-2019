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
import utility.GameCostants;
import utility.persistency.SaveGame;

import java.util.*;

/**
 * This class contains all the methods needed to handle entirely the Round of a player's turn
 */
public class RoundManager {
    private static final String TAGBACK_GRANADE = "TAGBACK_GRENADE";
    private static final String TELEPORTER = "TELEPORTER";
    private static final String NEWTON = "NEWTON";
    private static final String TARGETING_SCOPE = "TARGETING_SCOPE";

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
        if(gameInstance.getState() == GameState.NORMAL && turnManager.getTurnOwner().getPlayerState() == PossiblePlayerState.FIRST_SPAWN) {
            ActionManager.setStartingPossibleActions(turnManager.getTurnOwner(), gameInstance.isTerminatorPresent());
        } else if (gameInstance.getState() == GameState.NORMAL && turnManager.getTurnOwner().getPlayerState() == PossiblePlayerState.PLAYING) {
            ActionManager.setPossibleActions(turnManager.getTurnOwner());
        } else if (gameInstance.getState() == GameState.FINAL_FRENZY && turnManager.getTurnOwner().getPlayerState() == PossiblePlayerState.PLAYING) {
            ActionManager.setFrenzyPossibleActions(turnManager.getTurnOwner(), turnManager);
        }
    }

    /**
     * Method used to set <b>only</b> the {@link ReloadAction ReloadAction} to a {@link UserPlayer UserPlayer} when needed
     */
    private void setReloadAction() {
        turnManager.getTurnOwner().setActions(EnumSet.of(PossibleAction.RELOAD));
    }

    /**
     * Method that handles the FirstSpawn of the {@link Bot Terminator}, performed by the First
     * {@link UserPlayer Player} that starts the {@link Game Game}
     *
     * @param spawnRequest the {@link TerminatorSpawnRequest TerminatorSpawnRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleTerminatorFirstSpawn(TerminatorSpawnRequest spawnRequest) {
        if (turnManager.getTurnOwner().getPossibleActions().contains(PossibleAction.SPAWN_BOT)) {
            // terminator does not still exist!
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

        // every player must see the powerup the spawning one choosed to spawn
        gameManager.sendBroadcastMessage(new BroadcastSpawningPowerup(spawningPowerup));
        turnOwner.changePlayerState(PossiblePlayerState.PLAYING);
        setInitialActions();
        return buildPositiveResponse("Player spawned with chosen powerup");
    }

    /**
     * Method that handles the new {@link GameState GameState} in which the {@link Game Game} evolves after the
     * {@link TerminatorAction TerminatorAction} has been performed
     *
     * @param gameState the {@link GameState GameState} in which the {@link GameManager GameManager} needs to evolve
     */
    private void afterTerminatorActionHandler(PossibleGameState gameState) {
        if (gameState == PossibleGameState.GAME_STARTED) {
            // if terminator action is done before the 2 actions the game state does not change, otherwise it must be done before passing the turn
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
     * Method that handles the {@link TerminatorAction TerminatorAction}
     *
     * @param terminatorRequest the {@link UseTerminatorRequest UseTerminatorRequest} received
     * @param gameState         the {@link GameState GameState} used by the method
     *                          {@link #afterTerminatorActionHandler(PossibleGameState) afterTerminatorActionHandler}
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleTerminatorAction(UseTerminatorRequest terminatorRequest, PossibleGameState gameState) {
        TerminatorAction terminatorAction;

        if (turnManager.getTurnOwner().getPossibleActions().contains(PossibleAction.BOT_ACTION)) {
            try {
                terminatorAction = new TerminatorAction(turnManager.getTurnOwner(), gameInstance.getUserPlayerByUsername(terminatorRequest.getTargetPlayer()), terminatorRequest.getMovingPosition());
                if (terminatorAction.validate()) {
                    terminatorAction.execute();
                    turnManager.setDamagedPlayers(new ArrayList<>(List.of(gameInstance.getUserPlayerByUsername(terminatorRequest.getTargetPlayer()))));
                } else {
                    return buildNegativeResponse("Terminator action not valid");
                }
            } catch (InvalidActionException | PlayerNotFoundException e) {
                return buildNegativeResponse("Invalid Action ");
            }
        } else {
            return buildNegativeResponse("Player can not do this Action");
        }

        if (!turnManager.getDamagedPlayers().isEmpty()) {
            gameManager.changeState(PossibleGameState.GRANADE_USAGE);
            turnManager.setMarkedByGrenadePlayer(turnManager.getTurnOwner());
            turnManager.setMarkingTerminator(true);
            turnManager.giveTurn(turnManager.getDamagedPlayers().get(0));
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

        for(Integer index : granadeMessage.getPowerup()) {
            tempResponse = grenadeUsage(index, granadeMessage);
            if(tempResponse.getStatus() == MessageStatus.ERROR) {
                return tempResponse;
            }

            // else everything went right and I can discard the grenades used
        }

        // after having used all the grenades I discard them
        for(Integer index : granadeMessage.getPowerup()) {
            try {
                turnManager.getTurnOwner().discardPowerupByIndex(index);
            } catch (EmptyHandException e) {
                // never reached if the player has the powerup!
            }
        }

        turnManager.increaseCount();
        // if the player is the last one to use the granade I set back the state to the previous one and give the turn to the next player
        if (turnManager.getTurnCount() > turnManager.getDamagedPlayers().size() - 1) {
            turnManager.giveTurn(turnManager.getMarkedByGrenadePlayer());
            if(turnManager.getMarkingTerminator()) {
                afterTerminatorActionHandler(turnManager.getArrivingGameState());
            }
            gameManager.changeState(handleAfterActionState(turnManager.isSecondAction()));
            return buildPositiveResponse("Grenade used, turn going back to real turn owner");
        }

        // then I give the turn to the next damaged player
        turnManager.giveTurn(turnManager.getDamagedPlayers().get(turnManager.getTurnCount()));

        return buildGrenadePositiveResponse("Grenade has been Used, turn passed to the next possible user");
    }

    /**
     * Handles the usage of the grenade passed as an integer index
     *
     * @param index the index of the grenade to be used
     * @param granadeMessage the {@link PowerupRequest GranadeRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    private Response grenadeUsage(int index, PowerupRequest granadeMessage) {
        PowerupCard chosenGranade;

        if(index > turnManager.getTurnOwner().getPowerups().length) {
            return buildNegativeResponse("Invalid Powerup Index");
        }

        if(granadeMessage.getPowerup().size() > turnManager.getTurnOwner().getPowerups().length) {
            return buildNegativeResponse("Too many powerups!");
        }

        chosenGranade = turnManager.getTurnOwner().getPowerups()[index];

        if (!chosenGranade.getName().equals(TAGBACK_GRANADE)) {
            return buildNegativeResponse("Invalid Powerup");
        }

        // now I can set the target for the usage of the TAGBACK GRENADE
        if(gameInstance.isTerminatorPresent() && turnManager.getMarkingTerminator()) {
            granadeMessage.setGrenadeTarget(GameCostants.BOT_NAME);
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
        int sizeDifference;
        List<Integer> powerupsIndexes = scopeMessage.getPowerup();
        ArrayList<Integer> paymentPowerups = scopeMessage.getPaymentPowerups();
        List<String> targets = scopeMessage.getTargetPlayersUsername();

        // checks over every constraint of a SCOPE usage

        // index Check
        if(!checkScopeIndexes(powerupsIndexes, paymentPowerups)) {
            return buildNegativeResponse("Invalid Indexes in Request");
        }

        // targets Check
        if(!checkScopeTargets(targets)) {
            return buildNegativeResponse("Invalid Targets in Request");
        }

        if(powerupsIndexes.size() < targets.size()) {
            return buildNegativeResponse("Too many Targets");
        } else {
            sizeDifference = powerupsIndexes.size() - targets.size();
        }

        // ammo check
        if(powerupsIndexes.size() - paymentPowerups.size() != scopeMessage.getAmmoColor().size()) {
            return buildNegativeResponse("Missing Ammo Colors to pay");
        }

        switch (sizeDifference) {
            case 0:
                return oneScopeForEachTarget(scopeMessage);
            case 1:
                if (powerupsIndexes.size() == 3) {
                    return moreScopesForFirstTarget(scopeMessage);
                } else if (powerupsIndexes.size() == 2) {
                    return allScopesForOneTarget(scopeMessage,2);
                }

                // here is never reached because of previous checks
                return buildNegativeResponse(" Invalid Action ");
            case 2:
                return allScopesForOneTarget(scopeMessage, 3);
            default:
                return buildNegativeResponse(" Invalid Action ");
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
        ArrayList<Integer> paymentPowerups = scopeRequest.getPaymentPowerups();
        ArrayList<Ammo> ammoColors = scopeRequest.getAmmoColor();

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
                turnOwner.getPowerups()[i].use(tempRequest);
                turnOwner.discardPowerupByIndex(i);
            } catch (NotEnoughAmmoException e) {
                return buildNegativeResponse("Not Enough Ammo");
            } catch (InvalidPowerupActionException e) {
                return buildNegativeResponse(" Invalid Action");
            } catch (EmptyHandException e) {
                // can not happen here because powerup is already verified to be possessed
            }
        }

        gameManager.changeState(handleAfterActionState(turnManager.isSecondAction()));
        return buildPositiveResponse("Targeting Scope/s used ");
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
        ArrayList<Ammo> ammoColors = scopeRequest.getAmmoColor();

        for (int i = 0; i < 2; ++i) {
            // impossible that there are payment powerups in this case!
            tempRequest = new PowerupRequest.PowerupRequestBuilder(scopeRequest.getSenderUsername(), scopeRequest.getToken(), new ArrayList<>(List.of(scopeRequest.getPowerup().get(i))))
                    .targetPlayersUsername(new ArrayList<>(List.of(scopeRequest.getTargetPlayersUsername().get(0))))
                    .ammoColor(new ArrayList<>(List.of(ammoColors.get(0))))
                    .build();
            ammoColors.remove(0);

            try {
                turnOwner.getPowerups()[i].use(tempRequest);
                turnOwner.discardPowerupByIndex(i);
            } catch (NotEnoughAmmoException e) {
                return buildNegativeResponse("Not Enough Ammo");
            } catch (EmptyHandException e) {
                // can not happen here because powerup is already verified to be possessed
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
            turnOwner.discardPowerupByIndex(2);
        } catch (NotEnoughAmmoException e) {
            return buildNegativeResponse("Not Enough Ammo ");
        } catch (InvalidPowerupActionException e) {
            return buildNegativeResponse("Invalid Action  ");
        } catch (EmptyHandException e) {
            // can not happen here because powerup is already verified to be possessed
        }

        gameManager.changeState(handleAfterActionState(turnManager.isSecondAction()));
        return buildPositiveResponse("Targeting Scope/s used");
    }

    /**
     * Uses all targeting scopes on the same target
     *
     * @param scopeRequest the {@link PowerupRequest PowerupRequest} received
     * @param numberOfScopes number of targeting scopes to be used
     * @return positive or negative {@link Response Response} handled by the server
     */
    private Response allScopesForOneTarget(PowerupRequest scopeRequest, int numberOfScopes) {
        PowerupRequest tempRequest;
        UserPlayer turnOwner = turnManager.getTurnOwner();
        ArrayList<Integer> paymentPowerups = scopeRequest.getPaymentPowerups();
        ArrayList<Ammo> ammoColors = scopeRequest.getAmmoColor();

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
                turnOwner.getPowerups()[i].use(tempRequest);
                turnOwner.discardPowerupByIndex(i);
            } catch (NotEnoughAmmoException e) {
                return buildNegativeResponse("Not Enough Ammo ");
            } catch (InvalidPowerupActionException e) {
                return buildNegativeResponse("Invalid  Action");
            } catch (EmptyHandException e) {
                // cn not happen here because powerup is already verified to be possessed
            }
        }

        gameManager.changeState(handleAfterActionState(turnManager.isSecondAction()));
        return buildPositiveResponse("Targeting Scope/s used");
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

        if(powerupsIndexes.size() < paymentPowerups.size()) {
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
     * @param targetsUsernames the List of the SCOPE targets usernames
     * @return true
     */
    private boolean checkScopeTargets(List<String> targetsUsernames) {
        boolean targetPresent = true;

        if(targetsUsernames.isEmpty()) {
            return false;
        }

        for(int i = 0; i < targetsUsernames.size() && targetPresent; ++i) {
            for(Player damagedPlayers : turnManager.getDamagedPlayers()) {
                if(targetsUsernames.get(i).equals(damagedPlayers.getUsername())) {
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

        if (powerupRequest.getPowerup().get(0) < 0 || powerupRequest.getPowerup().get(0) > turnManager.getTurnOwner().getPowerups().length) {
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
        List<UserPlayer> beforeShootPlayers;

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
                beforeShootPlayers = gameInstance.getPlayers();
                shootAction.execute();
                turnManager.setDamagedPlayers(buildDamagedPlayers(beforeShootPlayers));
            } else {
                return buildNegativeResponse("Invalid Shoot Action");
            }
        } catch (WeaponAlreadyChargedException e) {
            return buildNegativeResponse("Trying to recharge an already charged weapon with frenzy shoot");
        } catch (NotEnoughAmmoException e) {
            return buildNegativeResponse("Not enough ammo to recharge a weapon with frenzy shoot");
        } catch (WeaponNotChargedException e) {
            return buildNegativeResponse("Not charged weapon can not be used to shoot");
        } catch (InvalidActionException e) {
            return buildNegativeResponse("Invalid Shoot Action");
        }

        if(checkScopeUsage(turnOwner)) {
            turnManager.setSecondAction(secondAction);
            gameManager.changeState(PossibleGameState.SCOPE_USAGE);

            return buildScopePositiveResponse("Shoot Action done, shooter can use a Scope");
        } else if(!turnManager.getDamagedPlayers().isEmpty()){
            gameManager.changeState(PossibleGameState.GRANADE_USAGE);
            turnManager.setMarkedByGrenadePlayer(turnManager.getTurnOwner());
            turnManager.setMarkingTerminator(false);
            turnManager.giveTurn(turnManager.getDamagedPlayers().get(0));
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
        }

        // after a reload action a player always passes his turn and the game has to manage the death players
        deathPlayersHandler(PossibleGameState.PASS_NORMAL_TURN);
        return buildPositiveResponse("Reload Action done");
    }

    /**
     * Method that builds an ArrayList of the damaged {@link UserPlayer UserPlayers} after a {@link ShootAction ShootAction},
     * used by the {@link #handleScopeUsage(PowerupRequest) handleScopeUsage} method to verify that the TARGETING SCOPE
     * is used only on a damaged {@link UserPlayer UserPlayer}
     *
     * @param beforeShootPlayers the List of {@link UserPlayer UserPlayers} before the {@link ShootAction ShootAction}
     * @return the ArrayList of damaged {@link UserPlayer UserPlayers}
     */
    private ArrayList<UserPlayer> buildDamagedPlayers(List<UserPlayer> beforeShootPlayers) {
        ArrayList<UserPlayer> reallyDamagedPlayers = new ArrayList<>();

        for (UserPlayer afterPlayer : gameInstance.getPlayers()) {
            for (UserPlayer beforePlayer : beforeShootPlayers) {
                if (afterPlayer.equals(beforePlayer) && afterPlayer.getPlayerBoard().getDamageCount() > beforePlayer.getPlayerBoard().getDamageCount()) {
                    reallyDamagedPlayers.add(afterPlayer);
                }
            }
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
            if (turnManager.getTurnOwner().equals(turnManager.getLastPlayer())) {
                // if reached, game has ended, last remaining points are calculated and a winner is declared!
                gameManager.endGame();
                return buildPositiveResponse("Turn passed and GAME HAS ENDED");
            }
            return deathPlayersHandler(PossibleGameState.PASS_FRENZY_TURN);
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
        ArrayList<UserPlayer> deathPlayers = gameInstance.getDeathPlayers();

        if (gameInstance.isTerminatorPresent() && gameInstance.getTerminator().getPlayerBoard().getDamageCount() > 10) {
            // first of all I control if the current player has done a double kill
            if (!gameInstance.getDeathPlayers().isEmpty()) {
                turnManager.getTurnOwner().addPoints(1);
            }

            gameInstance.getTerminator().setPosition(null);
            turnManager.setArrivingGameState(nextPassState);
            gameManager.changeState(PossibleGameState.TERMINATOR_RESPAWN);
            return buildPositiveResponse("Terminator has died respawn him before passing");
        } else if (!gameInstance.getDeathPlayers().isEmpty()) {
            // first of all I control if the current player has done a double kill
            if (gameInstance.getDeathPlayers().size() > 1) {
                turnManager.getTurnOwner().addPoints(1);
            }

            // there are death players I set everything I need to respawn them
            turnManager.setDeathPlayers(deathPlayers);
            gameManager.changeState(PossibleGameState.MANAGE_DEATHS);
            turnManager.giveTurn(deathPlayers.get(0));
            turnManager.getTurnOwner().setSpawningCard((PowerupCard) gameInstance.getPowerupCardsDeck().draw());
            turnManager.resetCount();
            turnManager.setArrivingGameState(nextPassState);
            return buildPositiveResponse("Turn passed");
        } else {
            // if no players have died the GameState remains the same
            return handleNextTurn(nextPassState);
        }
    }

    /**
     * Method that handles the Respawn of the {@link Bot Terminatore} performed by the TurnOwner
     *
     * @param respawnRequest the {@link TerminatorSpawnRequest TerminatorRespawnRequest} received
     * @return a positive or negative {@link Response Response} handled by the server
     */
    Response handleTerminatorRespawn(TerminatorSpawnRequest respawnRequest) {
        ArrayList<UserPlayer> deathPlayers = gameInstance.getDeathPlayers();

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
            deathPlayers.get(0).setSpawningCard((PowerupCard) gameInstance.getPowerupCardsDeck().draw());
            turnManager.resetCount();
            return buildPositiveResponse("Turn passed after Spawning the Terminator");
        } else {
            if (gameInstance.remainingSkulls() == 1) {   // last skull is going to be removed, frenzy mode has to be activated
                turnManager.setFrenzyPlayers();
                turnManager.setLastPlayer();
                return handleNextTurn(PossibleGameState.PASS_FRENZY_TURN);
            } else {
                return handleNextTurn(turnManager.getArrivingGameState());
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
            turnOwner.setSpawningCard(null);
        } else {
            spawnPowerup = turnOwner.getPowerups()[powerupIndex];
            try {
                turnOwner.discardPowerup(spawnPowerup);
            } catch (EmptyHandException e) {
                // can never occur! We have just verified that the turnOwner has this powerup!
            }
        }
        spawnColor = Ammo.toColor(spawnPowerup.getValue());

        // now that I know the color of the spawning square I can respawn the player
        try {
            gameInstance.spawnPlayer(turnOwner, gameInstance.getGameMap().getSpawnSquare(spawnColor));
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
        turnManager.getTurnOwner().setSpawningCard((PowerupCard) gameInstance.getPowerupCardsDeck().draw());

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
        setInitialActions();

        // then I reset the missing cards on the board
        gameInstance.getGameMap().addMissingCards();

        if (arrivingState == PossibleGameState.PASS_NORMAL_TURN) {
            gameManager.changeState(PossibleGameState.GAME_STARTED);
            return buildPositiveResponse("Turn Passed");
        } else if (arrivingState == PossibleGameState.PASS_FRENZY_TURN) {
            gameManager.changeState(PossibleGameState.FINAL_FRENZY);
            return buildPositiveResponse("Turn Passed");
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
        for(PowerupCard powerupCard : turnOwner.getPowerups()) {
            if(powerupCard.getName().equals(TARGETING_SCOPE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Method that handles the spawn of a {@link UserPlayer UserPlayer} while he disconnects from the game on the very
     * first round. If the {@link Bot Bot} is present his spawn is also managed
     *
     * @return always a positive response because this method always works alone
     */
    Response handleRandomSpawn() {
        int randomIndex = Game.rand.nextInt(1);

        PowerupCard spawningPowerup = getTurnManager().getTurnOwner().getPowerups()[randomIndex];
        RoomColor spawnColor = null;

        if (getTurnManager().getTurnOwner().getPossibleActions().contains(PossibleAction.SPAWN_BOT)) {
            // terminator does not still exist!
            try {
                gameInstance.buildTerminator();
                gameInstance.spawnTerminator(gameInstance.getGameMap().getSpawnSquare(RoomColor.getRandomSpawnColor()));
            } catch (InvalidSpawnColorException e) {
                // never reached random color is always a correct color
            }
        }

        if(getTurnManager().getTurnOwner().getPossibleActions().contains(PossibleAction.CHOOSE_SPAWN)) {
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

            // every player must see the powerup the spawning one choosed to spawn
            gameManager.sendBroadcastMessage(new BroadcastSpawningPowerup(spawningPowerup));
            getTurnManager().getTurnOwner().changePlayerState(PossiblePlayerState.PLAYING);
            setInitialActions();
        }

        return buildPositiveResponse("Player randomly spawned because disconnected while very first round");
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
     * @param reason the reason coming from the {@link ShootAction ShootAction}
     * @return the {@link MessageStatus NEED_PLAYER_ACTION} {@link Response Response} built
     */
    private Response buildScopePositiveResponse(String reason) {
        gameManager.sendPrivateUpdates();
        SaveGame.saveGame(gameManager);
        return new Response(reason, MessageStatus.NEED_PLAYER_ACTION);
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
