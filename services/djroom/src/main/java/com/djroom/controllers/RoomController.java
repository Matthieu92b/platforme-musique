package com.djroom.controllers;

import com.djroom.actors.PlaylistActor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final ActorSystem actorSystem;

    // Référence les RoomActor créés par roomId (stockage en mémoire côté API)
    private final ConcurrentMap<String, ActorRef> rooms = new ConcurrentHashMap<>();

    public RoomController(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Crée une nouvelle room et fait rejoindre l'utilisateur créateur.
     * POST /api/rooms
     */
    @PostMapping
    public ResponseEntity<CreateRoomResponse> createRoom(@RequestBody CreateRoomRequest req) {
        String roomId = "room-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("Creating room {} (owner={})", roomId, req.userId);

        try {
            // Demande au service chat-actor de créer le ChatActor pour cette room
            ActorRef chatManager = actorSystem.actorSelection("chat-actor/chat-manager");
            chatManager.tell(Message.of("CREATE_CHAT", roomId), null);

            // Création du RoomActor et enregistrement local
            ActorRef roomActor = actorSystem.actorOf(RoomActor.class, roomId);
            rooms.put(roomId, roomActor);

            // Le créateur rejoint automatiquement la room
            roomActor.tell(
                    Message.of("JOIN_ROOM", new RoomActor.JoinRoomMsg(req.userId)),
                    null
            );

            String roomName = (req.roomName != null && !req.roomName.isBlank())
                    ? req.roomName
                    : "New Room";

            return ResponseEntity.ok(new CreateRoomResponse(roomId, req.userId, roomName));

        } catch (Exception e) {
            log.error("Failed to create room {}", roomId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Rejoint une room existante.
     * POST /api/rooms/{roomId}/join
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<String> joinRoom(@PathVariable String roomId, @RequestBody JoinRoomRequest req) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Join room {} (user={})", roomId, req.userId);

        roomActor.tell(
                Message.of("JOIN_ROOM", new RoomActor.JoinRoomMsg(req.userId)),
                null
        );

        return ResponseEntity.ok("Joined room " + roomId);
    }

    /**
     * Quitte une room.
     * POST /api/rooms/{roomId}/leave
     */
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<String> leaveRoom(@PathVariable String roomId, @RequestBody LeaveRoomRequest req) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Leave room {} (user={})", roomId, req.userId);

        roomActor.tell(
                Message.of("LEAVE_ROOM", new RoomActor.LeaveRoomMsg(req.userId)),
                null
        );

        return ResponseEntity.ok("Left room " + roomId);
    }

    /**
     * Ajoute un track à la playlist de la room.
     * POST /api/rooms/{roomId}/tracks
     */
    @PostMapping("/{roomId}/tracks")
    public ResponseEntity<String> addTrack(@PathVariable String roomId, @RequestBody AddTrackRequest req) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Add track requested in room {} (user={}, title='{}')", roomId, req.userId, req.trackTitle);

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
     * Vote sur un track.
     * POST /api/rooms/{roomId}/tracks/{trackId}/vote
     */
    @PostMapping("/{roomId}/tracks/{trackId}/vote")
    public ResponseEntity<String> voteTrack(
            @PathVariable String roomId,
            @PathVariable long trackId,
            @RequestBody VoteTrackRequest req
    ) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Vote requested in room {} (user={}, trackId={}, delta={})", roomId, req.userId, trackId, req.delta);

        roomActor.tell(
                Message.of("VOTE_TRACK", new RoomActor.VoteTrackMsg(trackId, req.delta)),
                null
        );

        return ResponseEntity.accepted().body("Vote request sent");
    }

    /**
     * Passe au track suivant.
     * POST /api/rooms/{roomId}/next
     */
    @PostMapping("/{roomId}/next")
    public ResponseEntity<String> nextTrack(@PathVariable String roomId) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Next track requested for room {}", roomId);

        roomActor.tell(Message.of("NEXT", null), null);

        return ResponseEntity.accepted().body("Next track requested");
    }

    // =========================================================================
    // Player: play / pause
    // =========================================================================

    /**
     * Démarre la lecture.
     * POST /api/rooms/{roomId}/play
     */
    @PostMapping("/{roomId}/play")
    public ResponseEntity<String> play(@PathVariable String roomId) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Play requested for room {}", roomId);

        roomActor.tell(Message.of("PLAY", null), null);

        return ResponseEntity.accepted().body("Play requested");
    }

    /**
     * Met en pause la lecture.
     * POST /api/rooms/{roomId}/pause
     */
    @PostMapping("/{roomId}/pause")
    public ResponseEntity<String> pause(@PathVariable String roomId) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("Pause requested for room {}", roomId);

        roomActor.tell(Message.of("PAUSE", null), null);

        return ResponseEntity.accepted().body("Pause requested");
    }

    // =========================================================================
    // Chat
    // =========================================================================

    /**
     * Envoie un message dans le chat de la room.
     * POST /api/rooms/{roomId}/chat
     */
    @PostMapping("/{roomId}/chat")
    public ResponseEntity<String> sendChat(@PathVariable String roomId, @RequestBody ChatMessageRequest req) {
        ActorRef roomActor = rooms.get(roomId);
        if (roomActor == null) {
            return ResponseEntity.notFound().build();
        }

        // On loggue uniquement les méta-infos, pas le contenu du message (évite de logguer du contenu potentiellement sensible)
        log.info("Chat message requested in room {} (user={})", roomId, req.userId);

        roomActor.tell(
                Message.of("SEND_CHAT", new RoomActor.SendChatMsg(req.userId, req.message)),
                null
        );

        return ResponseEntity.accepted().body("Chat message sent");
    }

    /**
     * Récupère la playlist de la room via un "ask pattern" local.
     * GET /api/rooms/{roomId}/playlist
     */
    @GetMapping("/{roomId}/playlist")
    public ResponseEntity<?> getPlaylist(@PathVariable String roomId) {
        try {
            // Le PlaylistActor est local au service "djroom" : "djroom/playlist-" + roomId
            String playlistPath = "djroom/playlist-" + roomId;
            ActorRef playlistActor = actorSystem.actorSelection(playlistPath);

            CompletableFuture<PlaylistActor.PlaylistStateMsg> future = new CompletableFuture<>();

            // Le future est passé en payload et complété par le PlaylistActor
            playlistActor.tell(Message.of("GET_PLAYLIST", future), null);

            PlaylistActor.PlaylistStateMsg state = future.get(1, TimeUnit.SECONDS);
            return ResponseEntity.ok(state);

        } catch (IllegalArgumentException e) {
            log.warn("PlaylistActor not found for room {}", roomId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            // On différencie ici un échec de récupération (timeout / autre erreur)
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
