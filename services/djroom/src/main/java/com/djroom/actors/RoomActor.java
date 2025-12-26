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
 * Représente une room musicale.
 * - gère les membres et l'hôte
 * - orchestre la playlist (local) et les services distants (DJ / Chat)
 */
public class RoomActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(RoomActor.class);

    // Identifiant stable de la room (utilisé côté REST) : ex "room-b9a07e41"
    private final String roomId;

    // Membres présents dans la room
    private final Set<String> members = new HashSet<>();

    // Hôte (premier membre, ou réaffecté si l'hôte quitte)
    private String hostUserId;

    // Statut de la room (ACTIVE/CLOSED)
    private RoomState.RoomStatus status = RoomState.RoomStatus.ACTIVE;

    // Acteurs associés
    private ActorRef playlistActor;  // local (dans le même ActorSystem)
    private ActorRef djActor;        // distant (service djactor)
    private ActorRef chatActor;      // distant (service chat-actor)

    /**
     * Constructeur utilisé par ActorSystem.actorOf(RoomActor.class, "room-xxxx").
     */
    public RoomActor(String roomId) {
        this.roomId = roomId;
    }

    /**
     * Initialisation au démarrage :
     * - création de l'acteur local playlist
     * - référencement des acteurs distants (DJ/Chat) via actorSelection
     */
    @Override
    public void preStart(ActorContext ctx) throws Exception {
        log.info("RoomActor started for room {} at {}", roomId, ctx.self().path());

        // Création de l'acteur local de gestion de playlist pour cette room
        playlistActor = ctx.actorOf(PlaylistActor.class, "playlist-" + roomId);

        // Référence les acteurs distants (si disponibles) pour piloter la lecture et le chat
        try {
            djActor = ctx.actorSelection("djactor/dj-" + roomId);
            chatActor = ctx.actorSelection("chat-actor/chat-" + roomId);

            log.debug("Remote actors referenced for room {}", roomId);
        } catch (Exception e) {
            // Non bloquant : la room peut vivre même si les références distantes ne sont pas résolues au démarrage
            log.warn("Failed to reference remote actors for room {}: {}", roomId, e.getMessage());
        }
    }

    /**
     * Point d'entrée des messages de la room.
     * Route chaque commande vers un handler dédié.
     */
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

            // Réponses en provenance d'acteurs enfants
            case "TRACK_ADDED" -> handleTrackAdded((PlaylistActor.TrackAddedMsg) message.payload(), ctx);
            case "NEXT_TRACK" -> handleNextTrack((PlaylistActor.NextTrackMsg) message.payload(), ctx);

            default -> log.warn("Unknown message type for RoomActor {}: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }

    // ========== Handlers ==========

    /**
     * Ajoute un utilisateur à la room.
     * - le premier utilisateur devient l'hôte
     * - si c'est le premier membre, initialise le player côté DJActor
     * - notifie le ChatActor de l'entrée
     * - répond au sender (ex: API/WS gateway)
     */
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

        // Le premier membre devient host
        if (hostUserId == null) {
            hostUserId = msg.userId;
            log.info("User {} is now host of room {}", msg.userId, roomId);
        }

        log.info("User {} joined room {} (members={})", msg.userId, roomId, members.size());

        // Si premier membre : initialisation du player distant
        if (members.size() == 1 && djActor != null) {
            djActor.tell(
                    Message.of("INIT_PLAYER", new InitPlayerMsg(roomId)),
                    ctx.self()
            );
        }

        // Notification au service de chat
        if (chatActor != null) {
            chatActor.tell(
                    Message.of("USER_JOINED", new UserEventMsg(msg.userId, roomId)),
                    ctx.self()
            );
        }

        // Réponse au demandeur
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

    /**
     * Retire un utilisateur de la room.
     * - si la room devient vide, la ferme et stop les acteurs associés
     * - si l'hôte part, réaffecte l'hôte
     * - notifie le ChatActor de la sortie
     */
    private void handleLeaveRoom(LeaveRoomMsg msg, ActorContext ctx) {
        boolean removed = members.remove(msg.userId);

        if (!removed) {
            // Peut arriver si double leave côté client : pas bloquant
            log.warn("User {} not in room {}", msg.userId, roomId);
            return;
        }

        log.info("User {} left room {} (members remaining={})", msg.userId, roomId, members.size());

        // Si plus aucun membre, on ferme la room et on nettoie
        if (members.isEmpty()) {
            log.info("Room {} is empty, closing", roomId);
            status = RoomState.RoomStatus.CLOSED;

            // Stop l'acteur enfant local
            if (playlistActor != null) {
                ctx.stop(playlistActor);
            }

            // Demande aux services distants d'arrêter leurs ressources
            if (djActor != null) {
                djActor.tell(Message.of("STOP_PLAYER", roomId), ctx.self());
            }
            if (chatActor != null) {
                chatActor.tell(Message.of("CLOSE_CHAT", roomId), ctx.self());
            }

            // Stop l'acteur room
            ctx.stop(ctx.self());
            return;
        }

        // Si l'hôte part : réaffecte à un membre restant
        if (msg.userId.equals(hostUserId)) {
            hostUserId = members.iterator().next();
            log.info("New host for room {}: {}", roomId, hostUserId);
        }

        // Notification au service de chat
        if (chatActor != null) {
            chatActor.tell(
                    Message.of("USER_LEFT", new UserEventMsg(msg.userId, roomId)),
                    ctx.self()
            );
        }
    }

    /**
     * Ajoute un track via PlaylistActor.
     * Vérifie d'abord que l'utilisateur est bien membre de la room.
     */
    private void handleAddTrack(AddTrackMsg msg, ActorContext ctx) {
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

        log.info("Add track requested in room {}: title='{}'", roomId, msg.trackTitle);

        // Forward vers l'acteur playlist local
        playlistActor.tell(
                Message.of("ADD_TRACK", new PlaylistActor.AddTrackMsg(
                        msg.trackUrl,
                        msg.trackTitle,
                        msg.durationMs,
                        msg.userId
                )),
                ctx.self()
        );

        // La confirmation arrive via TRACK_ADDED (handleTrackAdded)
    }

    /**
     * Forward d'un vote vers PlaylistActor.
     * On forward le sender pour que la réponse (VOTE_SUCCESS/FAILED) revienne directement au demandeur.
     */
    private void handleVoteTrack(VoteTrackMsg msg, ActorContext ctx) {
        playlistActor.tell(
                Message.of("VOTE_TRACK", new PlaylistActor.VoteTrackMsg(msg.trackId, msg.delta)),
                ctx.sender()
        );
    }

    /**
     * Transmet la commande PLAY au DJActor.
     */
    private void handlePlay(ActorContext ctx) {
        log.info("Play command for room {}", roomId);

        if (djActor != null) {
            djActor.tell(Message.of("PLAY", roomId), ctx.self());
        }

        if (ctx.sender() != null) {
            ctx.sender().tell(Message.of("PLAY_COMMAND_SENT", null), ctx.self());
        }
    }

    /**
     * Transmet la commande PAUSE au DJActor.
     */
    private void handlePause(ActorContext ctx) {
        log.info("Pause command for room {}", roomId);

        if (djActor != null) {
            djActor.tell(Message.of("PAUSE", roomId), ctx.self());
        }

        if (ctx.sender() != null) {
            ctx.sender().tell(Message.of("PAUSE_COMMAND_SENT", null), ctx.self());
        }
    }

    /**
     * Demande à PlaylistActor le prochain track.
     * PlaylistActor gère l'ordre et notifie le DJActor du LOAD_TRACK.
     */
    private void handleNext(ActorContext ctx) {
        log.info("Next command for room {}", roomId);

        playlistActor.tell(
                Message.of("GET_NEXT_TRACK", null),
                ctx.self()
        );
    }

    /**
     * Envoie un message au ChatActor si l'utilisateur est membre.
     * On forward le sender pour que l'appelant reçoive directement l'ack éventuel.
     */
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
                    ctx.sender()
            );
        }
    }

    /**
     * Retourne l'état courant de la room.
     * Note : playlistSize est laissé à 0 ici car la taille n'est pas récupérée en synchrone.
     */
    private void handleGetState(ActorContext ctx) {
        RoomState state = new RoomState(
                roomId,
                new ArrayList<>(members),
                hostUserId,
                status,
                0
        );

        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("ROOM_STATE", state),
                    ctx.self()
            );
        }
    }

    /**
     * Forward la demande de playlist vers PlaylistActor.
     * On passe le sender pour que la réponse (PLAYLIST_STATE) revienne directement au demandeur.
     */
    private void handleGetPlaylist(ActorContext ctx) {
        playlistActor.tell(
                Message.of("GET_PLAYLIST", null),
                ctx.sender()
        );
    }

    /**
     * Confirmation interne : un track a été ajouté.
     * Utile pour brancher plus tard une notification websocket, métriques, etc.
     */
    private void handleTrackAdded(PlaylistActor.TrackAddedMsg msg, ActorContext ctx) {
        log.info("Track added confirmed in room {}: title='{}'", roomId, msg.track().getTitle());
    }

    /**
     * Notification interne : prochain track sélectionné.
     * PlaylistActor a déjà déclenché le chargement côté DJActor.
     */
    private void handleNextTrack(PlaylistActor.NextTrackMsg msg, ActorContext ctx) {
        log.info("Next track selected in room {}: title='{}'", roomId, msg.track().getTitle());
    }

    /**
     * Log de cycle de vie.
     */
    @Override
    public void postStop(ActorContext ctx) {
        log.info("RoomActor stopped for room {}", roomId);
    }


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
