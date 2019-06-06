package network.server;

import controller.GameManager;
import enumerations.MessageContent;
import enumerations.MessageStatus;
import model.Game;
import network.message.*;
import utility.persistency.SaveGame;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class is the main server class which starts a Socket and a RMI server.
 * It handles all the client regardless of whether they are Sockets or RMI
 */
public class Server implements Runnable {
    private static final int SOCKET_PORT = 2727;
    private static final int RMI_PORT = 7272;

    private static final int MAX_CLIENT = 5;
    private static final String[] FORBIDDEN_USERNAME = {Game.GOD, Game.BOT};

    private Map<String, Connection> clients;

    private final GameManager gameManager;

    public static final Logger LOGGER = Logger.getLogger("Server");

    private Server(boolean terminator, int skullNum) {
        clients = new HashMap<>();

        SocketServer serverSocket = new SocketServer(this, SOCKET_PORT);
        serverSocket.startServer();

        LOGGER.info("Socket Server Started");

        RMIServer rmiServer = new RMIServer(this, RMI_PORT);
        rmiServer.startServer();

        LOGGER.info("RMI Server Started");

        Thread pinger = new Thread(this);
        pinger.start();

        gameManager = new GameManager(this, terminator, skullNum);
    }

    private Server() {
        SocketServer serverSocket = new SocketServer(this, SOCKET_PORT);
        serverSocket.startServer();

        LOGGER.info("Socket Server Started");

        RMIServer rmiServer = new RMIServer(this, RMI_PORT);
        rmiServer.startServer();

        LOGGER.info("RMI Server Started");

        Thread pinger = new Thread(this);
        pinger.start();

        gameManager = SaveGame.loadGame(this);
    }

    public static void main(String[] args) {
        String confFilePath = "default/path";
        boolean terminator = false;
        int skullNum = 5;
        boolean reloadGame = false;

        // normal complete Server launch should have the following parameters: -l "confFilePath.txt" -b true/false -s #skulls
        // normal complete Server launch with game Reload should have the following parameter: -l "confFilePath.txt" -r

        if(args.length > 0 && args.length < 7) {
            for(int i = 0; i < args.length; ++i) {
                if(args[i].charAt(0) == '-' && args[i].length() == 2 && args.length >= i + 1 && args[i + 1].charAt(0) != '-') {
                    switch (args[i].charAt(1)) {
                        case 'l':
                            confFilePath = args[i + 1];
                            ++i;
                            break;
                        case 'b':
                            terminator = Boolean.parseBoolean(args[i + 1]);
                            ++i;
                            break;
                        case 's':
                            skullNum = Integer.parseInt(args[i + 1]);
                            ++i;
                            break;
                        case 'r':
                            reloadGame = true;
                            break;
                        default:
                            break;
                    }
                }
            }
        } else {
            new Server(terminator,  skullNum);
            return;
        }

        // if the starting command contains -r it means that a game is going to be reloaded
        if(reloadGame) {
            new Server();
            return;
        }

        // if the passed value is correct it is used for the game, if not DEFAULT value is set back to 5
        if(skullNum < 5 || skullNum > 8) {
            skullNum = 5;
        }

        // TODO pass confFilePath as it can be parsed in the server and used to set parameters
        new Server(terminator, skullNum);
    }

    /**
     * Adds or reconnects a player to the server
     *
     * @param username   username of the player
     * @param connection connection of the client
     */
    void login(String username, Connection connection) {
        try {
            if (clients.containsKey(username)) {
                if (!clients.get(username).isConnected()) { // Player Reconnection
                    clients.replace(username, connection);

                    String token = UUID.randomUUID().toString();
                    connection.setToken(token);

                    connection.sendMessage(
                            new ConnectionResponse("Successfully reconnected", token, MessageStatus.OK)
                    );

                    LOGGER.log(Level.INFO, "{0} reconnected to server!", username);
                } else { // Player already connected
                    connection.sendMessage(
                            new ConnectionResponse("Player already connected", null, MessageStatus.ERROR)
                    );

                    connection.disconnect();
                    LOGGER.log(Level.INFO, "{0} already connected to server!", username);
                }
            } else {
                if (clients.keySet().size() == MAX_CLIENT) { // Max players
                    connection.sendMessage(
                            new ConnectionResponse("Max number of player reached", null, MessageStatus.ERROR)
                    );

                    connection.disconnect();
                    LOGGER.log(Level.INFO, "{0} tried to connect but game is full!", username);
                } else { // New player
                    if (isUsernameLegit(username)) { // Username legit
                        clients.put(username, connection);

                        String token = UUID.randomUUID().toString();
                        connection.setToken(token);

                        connection.sendMessage(
                                new ConnectionResponse("Successfully connected", token, MessageStatus.OK)
                        );

                        LOGGER.log(Level.INFO, "{0} connected to server!", username);
                    } else { // Username not legit
                        connection.sendMessage(
                                new ConnectionResponse("Invalid Username", null, MessageStatus.ERROR)
                        );

                        connection.disconnect();
                        LOGGER.log(Level.INFO, "{0} tried to connect with invalid name!", username);
                    }
                }
            }
        } catch (IOException e) {
            connection.disconnect();
        }
    }

    /**
     * Checks if a username is legit by checking that is not equal to a forbidden username
     *
     * @param username username to check
     * @return if a username is legit
     */
    private boolean isUsernameLegit(String username) {
        for (String forbidden : FORBIDDEN_USERNAME) {
            if (username.equalsIgnoreCase(forbidden)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Process a message sent to server
     *
     * @param message message sent to server
     */
    void onMessage(Message message) {
        if (message != null && message.getSenderUsername() != null && (message.getToken() != null || message.getSenderUsername().equals("god"))) {
            LOGGER.log(Level.INFO, "Received: {0}", message);
            String msgToken = message.getToken();
            Connection conn = clients.get(message.getSenderUsername());

            if (conn == null) {
                LOGGER.log(Level.INFO, "Message Request {0} - Unknown username {1}", new Object[]{message.getContent().name(), message.getSenderUsername()});
            } else if (msgToken.equals(conn.getToken())) { // Checks that sender is the real player
                Message response = gameManager.onMessage(message);
                sendMessage(message.getSenderUsername(), response);
            }
        }
    }

    /**
     * Called when a player disconnects
     *
     * @param playerConnection connection of the player that just disconnected
     */
    void onDisconnect(Connection playerConnection) {
        String username = getUsernameByConnection(playerConnection);

        if (username != null) {
            sendMessageToAll(new DisconnectionMessage(username));
            gameManager.onMessage(new LobbyMessage(username, null, MessageContent.GET_IN_LOBBY, null, true));
            LOGGER.log(Level.INFO, "{0} disconnected from server!", username);
        }
    }

    /**
     * Sends a message to all clients
     *
     * @param message message to send
     */
    public void sendMessageToAll(Message message) {
        for (Map.Entry<String, Connection> client : clients.entrySet()) {
            if (client.getValue().isConnected()) {
                try {
                    client.getValue().sendMessage(message);
                } catch (IOException e) {
                    LOGGER.severe(e.getMessage());
                }
            }
        }
    }

    /**
     * Sends a message to a client
     *
     * @param username username of the client who will receive the message
     * @param message  message to send
     */
    public void sendMessage(String username, Message message) {
        for (Map.Entry<String, Connection> client : clients.entrySet()) {
            if (client.getKey().equals(username) && client.getValue().isConnected()) {
                try {
                    client.getValue().sendMessage(message);
                } catch (IOException e) {
                    LOGGER.severe(e.getMessage());
                }
                break;
            }
        }
    }

    /**
     * Returns the username of the connection owner
     *
     * @param connection connection to check
     * @return the username
     */
    private String getUsernameByConnection(Connection connection) {
        Set<String> usernameList = clients.entrySet()
                .stream()
                .filter(entry -> connection.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (usernameList.isEmpty()) {
            return null;
        } else {
            return usernameList.iterator().next();
        }
    }

    /**
     * Process that pings all the clients to check if they are still connected
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            for (Map.Entry<String, Connection> client : clients.entrySet()) {
                if (client.getValue().isConnected()) {
                    client.getValue().ping();
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOGGER.severe(e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}
