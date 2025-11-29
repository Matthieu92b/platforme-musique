package com.djroom.actors;

import com.djroom.models.RoomState;
import com.framework.actors.Actor;
import com.framework.actors.ActorContext;
import com.framework.actors.ActorRef;
import com.framework.actors.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a music room.
 * Manages members, coordinates playlist and player.
 */
public class RoomActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(RoomActor.class);

    // roomId tel qu'utilisé côté REST (ex: "room-b9a07e41")
    private final String roomId;

    private final Set<String> members = new HashSet<>();
    private String hostUserId;
    private RoomState.RoomStatus status = RoomState.RoomStatus.ACTIVE;

    private ActorRef playlistActor;  // LOCAL
    private ActorRef djActor;        // REMOTE (djactor service)
    private ActorRef chatActor;      // REMOTE (chat-actor service)

    // ✅ Constructeur utilisé par ActorSystem.actorOf(RoomActor.class, "room-xxxx")
    public RoomActor(String roomId) {
        this.roomId = roomId;
    }

    @Override
    public void preStart(ActorContext ctx) throws Exception {
        log.info("RoomActor started for room: {} at {}", roomId, ctx.self().path());

        // Create local PlaylistActor
        playlistActor = ctx.actorOf(PlaylistActor.class, "playlist-" + roomId);

        // Reference remote actors
        try {
            // ⚠ IMPORTANT : garder "dj-room-" ici, car côté djactor l'acteur s'appelle dj-room-<roomId>
            djActor = ctx.actorSelection("djactor/dj-" + roomId);
            chatActor = ctx.actorSelection("chat-actor/chat-" + roomId);

            log.debug("Remote actors referenced for room {}", roomId);
        } catch (Exception e) {
            log.warn("Failed to reference remote actors for room {}: {}", roomId, e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {

        log.info("[RoomActor {}] Received type={}", roomId, message.type());

        switch (message.type()) {
            case "JOIN_ROOM" -> handleJoinRoom((JoinRoomMsg) message.payload(), ctx);
            case "LEAVE_ROOM" -> handleLeaveRoom((LeaveRoomMsg) message.payload(), ctx);
            case "ADD_TRACK" -> handleAddTrack((AddTrackMsg) message.payload(), ctx);
            case "VOTE_TRACK" -> handleVoteTrack((VoteTrackMsg) message.payload(), ctx);
            case "PLAY" -> handlePlay(ctx);
            case "PAUSE" -> handlePause(ctx);
            case "NEXT" -> handleNext(ctx);
            case "SEND_CHAT" -> handleSendChat((SendChatMsg) message.payload(), ctx);
            case "GET_STATE" -> handleGetState(ctx);
            case "GET_PLAYLIST" -> handleGetPlaylist(ctx);

            // Responses from child actors
            case "TRACK_ADDED" -> handleTrackAdded((PlaylistActor.TrackAddedMsg) message.payload(), ctx);
            case "NEXT_TRACK" -> handleNextTrack((PlaylistActor.NextTrackMsg) message.payload(), ctx);

            default -> log.warn("Unknown message type for RoomActor {}: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }


    // ========== Handlers ==========

    private void handleJoinRoom(JoinRoomMsg msg, ActorContext ctx) {
        boolean isNewMember = members.add(msg.userId);

        if (!isNewMember) {
            log.info("User {} already in room {}", msg.userId, roomId);
            if (ctx.sender() != null) {
                ctx.sender().tell(
                        Message.of("ALREADY_JOINED", "User already in room"),
                        ctx.self()
                );
            }
            return;
        }

        // First member becomes host
        if (hostUserId == null) {
            hostUserId = msg.userId;
            log.info("User {} is now host of room {}", msg.userId, roomId);
        }

        log.info("User {} joined room {} ({} members)", msg.userId, roomId, members.size());

        // Initialize DJActor if this is the first member
        if (members.size() == 1 && djActor != null) {
            djActor.tell(
                    Message.of("INIT_PLAYER", new InitPlayerMsg(roomId)),
                    ctx.self()
            );
        }

        // Notify ChatActor
        if (chatActor != null) {
            chatActor.tell(
                    Message.of("USER_JOINED", new UserEventMsg(msg.userId, roomId)),
                    ctx.self()
            );
        }

        // Reply to sender
        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("ROOM_JOINED", new RoomJoinedMsg(
                            roomId,
                            members.size(),
                            hostUserId.equals(msg.userId)
                    )),
                    ctx.self()
            );
        }
    }

    private void handleLeaveRoom(LeaveRoomMsg msg, ActorContext ctx) {
        boolean removed = members.remove(msg.userId);

        if (!removed) {
            log.warn("User {} not in room {}", msg.userId, roomId);
            return;
        }

        log.info("User {} left room {} ({} members remaining)", msg.userId, roomId, members.size());

        // If room is empty, close it
        if (members.isEmpty()) {
            log.info("Room {} is empty, closing...", roomId);
            status = RoomState.RoomStatus.CLOSED;

            // Stop child actors
            ctx.stop(playlistActor);

            // Notify remote actors to stop
            if (djActor != null) {
                djActor.tell(Message.of("STOP_PLAYER", roomId), ctx.self());
            }
            if (chatActor != null) {
                chatActor.tell(Message.of("CLOSE_CHAT", roomId), ctx.self());
            }

            // Stop self
            ctx.stop(ctx.self());
            return;
        }

        // If host left, assign new host
        if (msg.userId.equals(hostUserId)) {
            hostUserId = members.iterator().next();
            log.info("New host for room {}: {}", roomId, hostUserId);
        }

        // Notify ChatActor
        if (chatActor != null) {
            chatActor.tell(
                    Message.of("USER_LEFT", new UserEventMsg(msg.userId, roomId)),
                    ctx.self()
            );
        }
    }

    private void handleAddTrack(AddTrackMsg msg, ActorContext ctx) {
        // Check if user is member
        if (!members.contains(msg.userId)) {
            log.warn("User {} not in room {}, cannot add track", msg.userId, roomId);
            if (ctx.sender() != null) {
                ctx.sender().tell(
                        Message.of("ERROR", "User not in room"),
                        ctx.self()
                );
            }
            return;
        }

        log.info("Adding track to room {}: {}", roomId, msg.trackTitle);

        // Forward to PlaylistActor
        playlistActor.tell(
                Message.of("ADD_TRACK", new PlaylistActor.AddTrackMsg(
                        msg.trackUrl,
                        msg.trackTitle,
                        msg.durationMs,
                        msg.userId
                )),
                ctx.self()
        );

        // Reply will come in handleTrackAdded
    }

    private void handleVoteTrack(VoteTrackMsg msg, ActorContext ctx) {
        // Forward to PlaylistActor
        playlistActor.tell(
                Message.of("VOTE_TRACK", new PlaylistActor.VoteTrackMsg(msg.trackId, msg.delta)),
                ctx.sender() // Forward sender so they get the response directly
        );
    }

    private void handlePlay(ActorContext ctx) {
        log.info("Play command for room {}", roomId);

        if (djActor != null) {
            djActor.tell(Message.of("PLAY", roomId), ctx.self());
        }

        if (ctx.sender() != null) {
            ctx.sender().tell(Message.of("PLAY_COMMAND_SENT", null), ctx.self());
        }
    }

    private void handlePause(ActorContext ctx) {
        log.info("Pause command for room {}", roomId);

        if (djActor != null) {
            djActor.tell(Message.of("PAUSE", roomId), ctx.self());
        }

        if (ctx.sender() != null) {
            ctx.sender().tell(Message.of("PAUSE_COMMAND_SENT", null), ctx.self());
        }
    }

    private void handleNext(ActorContext ctx) {
        log.info("Next command for room {}", roomId);

        // Ask PlaylistActor for next track
        playlistActor.tell(
                Message.of("GET_NEXT_TRACK", null),
                ctx.self()
        );
    }

    private void handleSendChat(SendChatMsg msg, ActorContext ctx) {
        if (!members.contains(msg.userId)) {
            if (ctx.sender() != null) {
                ctx.sender().tell(
                        Message.of("ERROR", "User not in room"),
                        ctx.self()
                );
            }
            return;
        }

        if (chatActor != null) {
            chatActor.tell(
                    Message.of("SEND_MESSAGE", new ChatMessageMsg(
                            msg.userId,
                            msg.message,
                            roomId
                    )),
                    ctx.sender() // Forward sender for direct response
            );
        }
    }

    private void handleGetState(ActorContext ctx) {
        RoomState state = new RoomState(
                roomId,
                new ArrayList<>(members),
                hostUserId,
                status,
                0 // playlistSize inconnu en synchrone
        );

        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("ROOM_STATE", state),
                    ctx.self()
            );
        }
    }

    private void handleGetPlaylist(ActorContext ctx) {
        playlistActor.tell(
                Message.of("GET_PLAYLIST", null),
                ctx.sender()
        );
    }

    private void handleTrackAdded(PlaylistActor.TrackAddedMsg msg, ActorContext ctx) {
        log.info("Track added confirmation in room {}: {}", roomId, msg.track().getTitle());
        // ici tu pourras notifier les membres via WebSocket, etc.
    }

    private void handleNextTrack(PlaylistActor.NextTrackMsg msg, ActorContext ctx) {
        log.info("Loading next track in room {}: {}", roomId, msg.track().getTitle());
        // PlaylistActor a déjà notifié DJActor
    }

    @Override
    public void postStop(ActorContext ctx) {
        log.info("RoomActor stopped for room: {}", roomId);
    }

    // ========== Messages ==========

    public record JoinRoomMsg(String userId) {}
    public record LeaveRoomMsg(String userId) {}
    public record AddTrackMsg(String userId, String trackUrl, String trackTitle, long durationMs) {}
    public record VoteTrackMsg(long trackId, int delta) {}
    public record SendChatMsg(String userId, String message) {}

    public record RoomJoinedMsg(String roomId, int memberCount, boolean isHost) {}
    public record InitPlayerMsg(String roomId) {}
    public record UserEventMsg(String userId, String roomId) {}
    public record ChatMessageMsg(String userId, String message, String roomId) {}
}
