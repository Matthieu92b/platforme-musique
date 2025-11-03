package com.djactor.messages;

import com.djactor.services.PlayerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PlayerStateListener {
    private final PlayerService playerService;

    public PlayerStateListener(PlayerService playerService) {
        this.playerService = playerService;
    }

    @RabbitListener(queues = "add-player-state")
    public void handleAddPlayerState(int roomID) {
        playerService.addPlayerState(roomID);
    }

    @RabbitListener(queues = "remove-player-state")
    public void handleDeletePlayerState(int roomID) {
        playerService.deletePlayerState(roomID);
    }

    @RabbitListener(queues = "toggle-play-pause")
    public void handleTogglePlayPause(int roomID) {
        playerService.getPlayerState(roomID).togglePlayPause();
    }

    @RabbitListener(queues = "next-song")
    public void handleNextTrack(int roomID) {
        playerService.getPlayerState(roomID).nextSong();
    }

    @RabbitListener(queues = "prev-song")
    public void handlePreviousTrack(int roomID) {
        playerService.getPlayerState(roomID).beginningOfSong();
    }
}
