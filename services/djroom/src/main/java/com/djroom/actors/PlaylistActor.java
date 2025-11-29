package com.djroom.actors;

import com.djroom.models.PlaylistTrack;
import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the playlist for a specific room.
 * Handles adding tracks, voting, and track ordering.
 */
public class PlaylistActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(PlaylistActor.class);

    // roomId associé à cette playlist (ex: room-b9a07e41)
    private String roomId;

    private final List<PlaylistTrack> tracks = new ArrayList<>();
    private long nextTrackId = 1;

    // Constructeur par défaut
    public PlaylistActor() {
    }

    // Constructeur utilisé éventuellement par le framework : PlaylistActor(String name)
    // name vaudra "playlist-room-XXXX"
    public PlaylistActor(String name) {
        // On laisse la logique d'extraction dans preStart()
    }

    @Override
    public void preStart(ActorContext ctx) {
        // path ex: "djroom/playlist-room-b9a07e41"
        String path = ctx.self().path();
        int idx = path.lastIndexOf('/');
        String localName = (idx >= 0) ? path.substring(idx + 1) : path;

        if (localName.startsWith("playlist-")) {
            this.roomId = localName.substring("playlist-".length()); // "room-b9a07e41"
        } else {
            this.roomId = localName;
        }

        log.info("📋 PlaylistActor started for room: {}", roomId);
    }

    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {

        log.info("[PlaylistActor {}] Received type={}", roomId, message.type());

        switch (message.type()) {
            case "ADD_TRACK" -> handleAddTrack((AddTrackMsg) message.payload(), ctx);
            case "VOTE_TRACK" -> handleVote((VoteTrackMsg) message.payload(), ctx);
            case "GET_NEXT_TRACK" -> handleGetNextTrack(ctx);
            case "GET_PLAYLIST" -> handleGetPlaylist(ctx);
            case "REMOVE_TRACK" -> handleRemoveTrack((RemoveTrackMsg) message.payload(), ctx);
            default -> log.warn("Unknown message type for PlaylistActor {}: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }


    private void handleAddTrack(AddTrackMsg msg, ActorContext ctx) {
        PlaylistTrack track = new PlaylistTrack(
                nextTrackId++,
                msg.url,
                msg.title,
                msg.durationMs,
                0, // Initial score
                Instant.now(),
                msg.addedBy
        );

        tracks.add(track);
        sortPlaylist();

        log.info("🎵 Track added to room {}: {} (id: {})", roomId, track.getTitle(), track.getId());

        // Notify the sender (RoomActor)
        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("TRACK_ADDED", new TrackAddedMsg(track)),
                    ctx.self()
            );
        }

        // If it's the first track, notify DJActor to load it
        if (tracks.size() == 1) {
            notifyDJActorLoadTrack(track, ctx);
        }
    }

    private void handleVote(VoteTrackMsg msg, ActorContext ctx) {
        Optional<PlaylistTrack> trackOpt = tracks.stream()
                .filter(t -> t.getId() == msg.trackId)
                .findFirst();

        if (trackOpt.isEmpty()) {
            if (ctx.sender() != null) {
                ctx.sender().tell(
                        Message.of("VOTE_FAILED", "Track not found"),
                        ctx.self()
                );
            }
            return;
        }

        PlaylistTrack track = trackOpt.get();
        int oldScore = track.getScore();
        track.addScore(msg.delta); // +1 or -1

        sortPlaylist();

        log.info("👍 Vote on track {} in room {}: {} → {}",
                track.getTitle(), roomId, oldScore, track.getScore());

        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("VOTE_SUCCESS", new VoteSuccessMsg(track.getId(), track.getScore())),
                    ctx.self()
            );
        }
    }

    private void handleGetNextTrack(ActorContext ctx) {
        if (tracks.isEmpty()) {
            log.info("⚠️  No more tracks in playlist for room {}", roomId);
            if (ctx.sender() != null) {
                ctx.sender().tell(Message.of("NO_TRACK", null), ctx.self());
            }
            return;
        }

        // Remove the first track (highest score)
        PlaylistTrack next = tracks.remove(0);

        log.info("⏭️  Next track for room {}: {}", roomId, next.getTitle());

        // Send to DJActor to load
        notifyDJActorLoadTrack(next, ctx);

        // Notify sender
        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("NEXT_TRACK", new NextTrackMsg(next)),
                    ctx.self()
            );
        }
    }

    private void handleGetPlaylist(ActorContext ctx) {
        List<PlaylistTrack> snapshot = new ArrayList<>(tracks);

        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("PLAYLIST_STATE", new PlaylistStateMsg(snapshot)),
                    ctx.self()
            );
        }
    }

    private void handleRemoveTrack(RemoveTrackMsg msg, ActorContext ctx) {
        boolean removed = tracks.removeIf(t -> t.getId() == msg.trackId);

        if (removed) {
            log.info("🗑️  Track {} removed from room {}", msg.trackId, roomId);
            if (ctx.sender() != null) {
                ctx.sender().tell(Message.of("TRACK_REMOVED", msg.trackId), ctx.self());
            }
        } else {
            if (ctx.sender() != null) {
                ctx.sender().tell(Message.of("REMOVE_FAILED", "Track not found"), ctx.self());
            }
        }
    }

    /**
     * Sort playlist by score (DESC) then FIFO
     */
    private void sortPlaylist() {
        tracks.sort(PlaylistTrack.BY_SCORE_DESC_THEN_FIFO);
    }

    /**
     * Notify DJActor to load a track
     */
    private void notifyDJActorLoadTrack(PlaylistTrack track, ActorContext ctx) {
        try {
            String targetPath = "djactor/dj-" + roomId;
            log.debug("📤 notifyDJActorLoadTrack → {}", targetPath);

            var djActor = ctx.actorSelection(targetPath);
            djActor.tell(
                    Message.of("LOAD_TRACK", new LoadTrackMsg(
                            track.getId(),
                            track.getUrl(),
                            track.getTitle(),
                            track.getDurationMs()
                    )),
                    ctx.self()
            );
            log.debug("📤 Sent LOAD_TRACK to DJActor for room {}", roomId);
        } catch (Exception e) {
            log.error("Failed to notify DJActor", e);
        }
    }


    @Override
    public void postStop(ActorContext ctx) {
        log.info("📋 PlaylistActor stopped for room: {}", roomId);
    }

    // ========== Messages ==========

    public record AddTrackMsg(String url, String title, long durationMs, String addedBy) {}
    public record VoteTrackMsg(long trackId, int delta) {}
    public record RemoveTrackMsg(long trackId) {}

    public record TrackAddedMsg(PlaylistTrack track) {}
    public record VoteSuccessMsg(long trackId, int newScore) {}
    public record NextTrackMsg(PlaylistTrack track) {}
    public record PlaylistStateMsg(List<PlaylistTrack> tracks) {}

    public record LoadTrackMsg(long id, String url, String title, long durationMs) {}
}
