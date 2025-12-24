package com.djroom.controllers;

import com.djroom.actors.RoomActor;
import com.framework.actors.ActorRef;
import com.framework.actors.ActorSystem;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.djroom.actors.PlaylistActor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final ActorSystem actorSystem;
    private final ConcurrentMap<String, ActorRef> rooms = new ConcurrentHashMap<>();

    public RoomController(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }


    /**
     * Create a new room
     * POST /api/rooms
     */
    @PostMapping
    public ResponseEntity<CreateRoomResponse> createRoom(@RequestBody CreateRoomRequest req) {
        String roomId = "room-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("Creating room {} for user {}", roomId, req.userId);

        try {
            // ✅ 1) Demande au service chat-actor de créer le ChatActor de cette room
            ActorRef chatManager = actorSystem.actorSelection("chat-actor/chat-manager");
            chatManager.tell(Message.of("CREATE_CHAT", roomId), null);

            // ✅ 2) Crée RoomActor
            ActorRef roomActor = actorSystem.actorOf(RoomActor.class, roomId);
            rooms.put(roomId, roomActor);

            roomActor.tell(
                    Message.of("JOIN_ROOM", new RoomActor.JoinRoomMsg(req.userId)),
                    null
            );

            return ResponseEntity.ok(new CreateRoomResponse(
                    roomId,
                    req.userId,
                    (req.roomName != null && !req.roomName.isBlank())
                            ? req.roomName
                            : "New Room"
            ));

        } catch (Exception e) {
            log.error("Failed to create room", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Join a room
     * POST /api/rooms/{roomId}/join
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<String> joinRoom(
            @PathVariable String roomId,
            @RequestBody JoinRoomRequest req) {


        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("User {} joining room {}", req.userId, roomId);

        roomActor.tell(
                Message.of("JOIN_ROOM", new RoomActor.JoinRoomMsg(req.userId)),
                null
        );

        return ResponseEntity.ok("Joined room " + roomId);
    }

    /**
     * Leave a room
     * POST /api/rooms/{roomId}/leave
     */
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<String> leaveRoom(
            @PathVariable String roomId,
            @RequestBody LeaveRoomRequest req) {

        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("User {} leaving room {}", req.userId, roomId);

        roomActor.tell(
                Message.of("LEAVE_ROOM", new RoomActor.LeaveRoomMsg(req.userId)),
                null
        );

        return ResponseEntity.ok("Left room " + roomId);
    }


    /**
     * Add track to room playlist
     * POST /api/rooms/{roomId}/tracks
     */
    @PostMapping("/{roomId}/tracks")
    public ResponseEntity<String> addTrack(
            @PathVariable String roomId,
            @RequestBody AddTrackRequest req) {

        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("User {} adds track {} to room {}",
                req.userId, req.trackTitle, roomId);

        roomActor.tell(
                Message.of("ADD_TRACK", new RoomActor.AddTrackMsg(
                        req.userId,
                        req.trackUrl,
                        req.trackTitle,
                        req.durationMs
                )),
                null
        );

        return ResponseEntity.accepted().body("Track add request sent");
    }

    /**
     * Vote for a track
     * POST /api/rooms/{roomId}/tracks/{trackId}/vote
     */
    @PostMapping("/{roomId}/tracks/{trackId}/vote")
    public ResponseEntity<String> voteTrack(
            @PathVariable String roomId,
            @PathVariable long trackId,
            @RequestBody VoteTrackRequest req) {

        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("User {} votes {} on track {} in room {}",
                req.userId, req.delta, trackId, roomId);

        roomActor.tell(
                Message.of("VOTE_TRACK", new RoomActor.VoteTrackMsg(trackId, req.delta)),
                null
        );

        return ResponseEntity.accepted().body("Vote request sent");
    }

    /**
     * Next track
     * POST /api/rooms/{roomId}/next
     */
    @PostMapping("/{roomId}/next")
    public ResponseEntity<String> nextTrack(@PathVariable String roomId) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Next track requested for room {}", roomId);

        roomActor.tell(
                Message.of("NEXT", null),
                null
        );

        return ResponseEntity.accepted().body("Next track requested");
    }

    // =========================================================================
    // Player: play / pause
    // =========================================================================

    /**
     * Play
     * POST /api/rooms/{roomId}/play
     */
    @PostMapping("/{roomId}/play")
    public ResponseEntity<String> play(@PathVariable String roomId) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Play requested for room {}", roomId);

        roomActor.tell(
                Message.of("PLAY", null),
                null
        );

        return ResponseEntity.accepted().body("Play requested");
    }

    /**
     * Pause
     * POST /api/rooms/{roomId}/pause
     */
    @PostMapping("/{roomId}/pause")
    public ResponseEntity<String> pause(@PathVariable String roomId) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Pause requested for room {}", roomId);

        roomActor.tell(
                Message.of("PAUSE", null),
                null
        );

        return ResponseEntity.accepted().body("Pause requested");
    }

    // =========================================================================
    // Chat
    // =========================================================================

    /**
     * Send chat message
     * POST /api/rooms/{roomId}/chat
     */
    @PostMapping("/{roomId}/chat")
    public ResponseEntity<String> sendChat(
            @PathVariable String roomId,
            @RequestBody ChatMessageRequest req) {

        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("User {} sends chat to room {}: {}",
                req.userId, roomId, req.message);

        roomActor.tell(
                Message.of("SEND_CHAT", new RoomActor.SendChatMsg(req.userId, req.message)),
                null
        );

        return ResponseEntity.accepted().body("Chat message sent");
    }
    @GetMapping("/{roomId}/playlist")
    public ResponseEntity<?> getPlaylist(@PathVariable String roomId) {
        try {
            // Path de l'acteur : serviceName "djroom" + nom "playlist-" + roomId
            String playlistPath = "djroom/playlist-" + roomId;

            ActorRef playlistActor = actorSystem.actorSelection(playlistPath);

            CompletableFuture<PlaylistActor.PlaylistStateMsg> future = new CompletableFuture<>();

            // On passe le future en payload (ask pattern local)
            playlistActor.tell(Message.of("GET_PLAYLIST", future), null);

            PlaylistActor.PlaylistStateMsg state = future.get(1, TimeUnit.SECONDS);

            // ✅ Spring va sérialiser PlaylistStateMsg en JSON : { "tracks": [ ... ] }
            return ResponseEntity.ok(state);

        } catch (IllegalArgumentException e) {
            // actorSelection a échoué (room inexistante / pas de playlist pour cette room)
            log.warn("PlaylistActor not found for room {}", roomId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to get playlist for room {}", roomId, e);
            return ResponseEntity.status(504).body("Timeout while getting playlist");
        }
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public static class CreateRoomRequest {
        public String userId;
        public String roomName;
    }

    public static class CreateRoomResponse {
        public String roomId;
        public String userId;
        public String roomName;

        public CreateRoomResponse(String roomId, String userId, String roomName) {
            this.roomId = roomId;
            this.userId = userId;
            this.roomName = roomName;
        }
    }

    public static class JoinRoomRequest {
        public String userId;
    }

    public static class LeaveRoomRequest {
        public String userId;
    }

    public static class AddTrackRequest {
        public String userId;
        public String trackUrl;
        public String trackTitle;
        public long durationMs;
    }

    public static class VoteTrackRequest {
        public String userId;
        /** +1 ou -1 */
        public int delta;
    }

    public static class ChatMessageRequest {
        public String userId;
        public String message;
    }
}
