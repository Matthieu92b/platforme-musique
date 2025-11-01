package com.djactor.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.djactor.models.PlayerStateManager;

/**
 * service to manage all the apps player states
 * manages autoincrementing timers for each player state
 * 
 * should be 1 state manager per room
 */
@Service
public class PlayerService {
    private Map<Integer, PlayerStateManager> playerStates = new HashMap<>();

    public PlayerService() { // default constructor because spring sucks
    }

    public PlayerService(int roomID) {
        playerStates.put(roomID, new PlayerStateManager());
    }

    /**
     * if playing, increments the timer of all player states every 250ms
     */
    @Scheduled(fixedRate = 250)
    public synchronized void updateStateManagersTimer() {
        playerStates.values().forEach(stateManager -> {
            stateManager.incrementPosition();
        });
    }

    /**
     * add/get an existing PlayerStateManager for a player id
     * 
     * @param roomID corresponding room id
     * @return new PlayerStateManager if none existed, existing one otherwise
     */
    public synchronized PlayerStateManager addPlayerState(int roomID) {
        return playerStates.computeIfAbsent(roomID, id -> new PlayerStateManager());
    }

    /**
     * @return the manager for the given id, null if none existed
     */
    public synchronized PlayerStateManager deletePlayerState(int roomID) {
        return playerStates.remove(roomID);
    }

    /**
     * @return PlayerStateManager if present, null otherwise
     */
    public synchronized PlayerStateManager getPlayerState(int roomID) {
        return playerStates.get(roomID);
    }

    /**
     * @return PlayerStates map
     */
    public synchronized Map<Integer, PlayerStateManager> getPlayerStates() {
        return playerStates;
    }
}
