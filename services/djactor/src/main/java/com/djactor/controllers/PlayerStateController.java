package com.djactor.controllers;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.djactor.models.PlayerStatus;
import com.djactor.services.PlayerService;

@RestController
@RequestMapping("/playerstate")
public class PlayerStateController {

    private final RabbitTemplate rabbitTemplate;
    private final PlayerService playerService;

    public PlayerStateController(PlayerService playerService, RabbitTemplate rabbitTemplate) {
        this.playerService = playerService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/add-player-state/{roomID}")
    public String togglePlayPause(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("add-player-state", id);
        return "id: " + id;
    }

    @PostMapping("/remove-player-state/{roomID}")
    public void addPlayerState(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("remove-player-state", id);
    }

    @PostMapping("/{roomID}/toggle")
    public void deletePlayerState(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("toggle-play-pause", id);
    }

    @PostMapping("/{roomID}/next")
    public void nextTrack(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("next-song", id);
    }

    @PostMapping("/{roomID}/prev")
    public void previousTrack(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("prev-song", id);
    }

    @GetMapping("/{roomID}/status") // either playing stopped or paused
    public PlayerStatus getCurrentStatus(@PathVariable("roomID") int id) {
        return playerService.getPlayerState(id).getStatus();
    }

    @GetMapping("/{roomID}/current-track")
    public int getCurrentTrack(@PathVariable("roomID") int id) {
        return playerService.getPlayerState(id).getCurrentTrackID();
    }

    @GetMapping("/{roomID}/current-position")
    public long getCurrentPosition(@PathVariable("roomID") int id) {
        return playerService.getPlayerState(id).getPositionMs();
    }
}