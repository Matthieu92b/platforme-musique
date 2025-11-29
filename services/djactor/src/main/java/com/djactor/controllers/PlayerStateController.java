/*package com.djactor.controllers;

import java.time.Instant;
import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.djactor.models.Track;
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

    // add player state for room
    @PostMapping("/add-player-state/{roomID}")
    public String addPlayerState(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("add-player-state", id);
        return "added player state for room: " + id;
    }

    // remove player state for room
    @PostMapping("/remove-player-state/{roomID}")
    public String deletePlayerState(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("remove-player-state", id);
        return "removed player state for room: " + id;
    }

    // toggle play/pause
    @PostMapping("/{roomID}/toggle")
    public void togglePlayPause(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("toggle-play-pause", id);
    }

    // skip to next track
    @PostMapping("/{roomID}/next")
    public void nextTrack(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("next-song", id);
    }

    // beginning of current track
    @PostMapping("/{roomID}/prev")
    public void beginningOfTrack(@PathVariable("roomID") int id) {
        rabbitTemplate.convertAndSend("prev-song", id);
    }

    @GetMapping("/{roomID}/status") // either playing stopped or paused
    public PlayerStatus getCurrentStatus(@PathVariable("roomID") int id) {
        return playerService.getPlayerState(id).getStatus();
    }

    @GetMapping("/{roomID}/current-track")
    public Track getCurrentTrack(@PathVariable("roomID") int id) {
        return playerService.getPlayerState(id).getCurrentTrack();
    }

    @GetMapping("/{roomID}/current-position")
    public long getCurrentPosition(@PathVariable("roomID") int id) {
        return playerService.getPlayerState(id).getPositionMs();
    }

    @PostMapping("/{roomID}/add-track")
    public String addTrack(
            @PathVariable("roomID") int id,
            @RequestParam("trackId") long trackId,
            @RequestParam("title") String title,
            @RequestParam("url") String url,
            @RequestParam("durationMs") long durationMs) {
        playerService.getPlayerState(id).addSongToPlaylist(trackId, title, url, 0, durationMs, Instant.now());
        return "Track " + title + " added to room " + id;
    }

    @GetMapping("/{roomID}/playlist")
    public List<Track> getPlaylist(@PathVariable("roomID") int id) {
        return playerService.getPlayerState(id).getPlaylist().getState();
    }
}*/