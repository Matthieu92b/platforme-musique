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
 * Gère la playlist d'une room :
 * - ajout de tracks
 * - votes (+/-) et tri
 * - récupération de la playlist
 * - suppression d'un track
 * - fourniture du "next track" et notification du DJActor
 */
public class PlaylistActor implements Actor {

    private static final Logger log = LoggerFactory.getLogger(PlaylistActor.class);

    // Identifiant de la room associée à cet acteur (ex: room-b9a07e41)
    private String roomId;

    // Liste des tracks en mémoire (ordre maintenu via sortPlaylist())
    private final List<PlaylistTrack> tracks = new ArrayList<>();

    // Id auto-incrémenté pour identifier les tracks
    private long nextTrackId = 1;

    /**
     * Constructeur par défaut.
     * Le roomId est déterminé dans preStart() à partir du path de l'acteur.
     */
    public PlaylistActor() {
    }

    /**
     * Constructeur éventuellement utilisé par le framework (signature compatible).
     * Ici on ne dépend pas du paramètre : l'identification se fait dans preStart().
     */
    public PlaylistActor(String name) {
        // Intentionnellement vide
    }

    /**
     * Initialisation au démarrage : dérive roomId depuis le path.
     * Exemple de path : "djroom/playlist-room-b9a07e41" -> roomId="room-b9a07e41"
     */
    @Override
    public void preStart(ActorContext ctx) {
        String path = ctx.self().path();
        int idx = path.lastIndexOf('/');
        String localName = (idx >= 0) ? path.substring(idx + 1) : path;

        if (localName.startsWith("playlist-")) {
            this.roomId = localName.substring("playlist-".length());
        } else {
            this.roomId = localName;
        }

        // Log de cycle de vie : utile pour diagnostiquer le démarrage et la room ciblée
        log.info("PlaylistActor started for room {}", roomId);
    }

    /**
     * Point d'entrée des messages.
     * Chaque type est routé vers un handler dédié.
     */
    @Override
    public CompletableFuture<Void> onReceive(Message message, ActorContext ctx) {
        // Log de réception : utile pour tracer les interactions entre acteurs
        log.info("[PlaylistActor {}] Received type={}", roomId, message.type());

        switch (message.type()) {
            case "ADD_TRACK" -> handleAddTrack((AddTrackMsg) message.payload(), ctx);
            case "VOTE_TRACK" -> handleVote((VoteTrackMsg) message.payload(), ctx);
            case "GET_NEXT_TRACK" -> handleGetNextTrack(ctx);
            case "GET_PLAYLIST" -> handleGetPlaylist(message.payload(), ctx);
            case "REMOVE_TRACK" -> handleRemoveTrack((RemoveTrackMsg) message.payload(), ctx);
            default -> log.warn("Unknown message type for PlaylistActor {}: {}", roomId, message.type());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Ajoute un track, trie la playlist, notifie le sender, et si la playlist était vide,
     * déclenche le chargement côté DJActor (premier track).
     */
    private void handleAddTrack(AddTrackMsg msg, ActorContext ctx) {
        boolean wasEmptyBefore = tracks.isEmpty();

        PlaylistTrack track = new PlaylistTrack(
                nextTrackId++,
                msg.url,
                msg.title,
                msg.durationMs,
                0, // score initial
                Instant.now(),
                msg.addedBy
        );

        tracks.add(track);
        sortPlaylist();

        log.info("Track added to room {}: title='{}', id={}", roomId, track.getTitle(), track.getId());

        // Notifie l'acteur appelant (souvent RoomActor) que le track a été ajouté
        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("TRACK_ADDED", new TrackAddedMsg(track)),
                    ctx.self()
            );
        }

        // Si c'était le premier track, on demande au DJActor de le charger
        if (wasEmptyBefore && !tracks.isEmpty()) {
            notifyDJActorLoadTrack(tracks.get(0), ctx);
        }
    }

    /**
     * Applique un vote (+1 / -1) sur un track, retrie, et renvoie le nouveau score.
     */
    private void handleVote(VoteTrackMsg msg, ActorContext ctx) {
        Optional<PlaylistTrack> trackOpt = tracks.stream()
                .filter(t -> t.getId() == msg.trackId)
                .findFirst();

        if (trackOpt.isEmpty()) {
            // Retour explicite en cas d'erreur fonctionnelle
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

        track.addScore(msg.delta);
        sortPlaylist();

        log.info("Vote applied in room {}: trackId={}, '{}' score {} -> {}",
                roomId, track.getId(), track.getTitle(), oldScore, track.getScore());

        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("VOTE_SUCCESS", new VoteSuccessMsg(track.getId(), track.getScore())),
                    ctx.self()
            );
        }
    }

    /**
     * Retourne le prochain track (celui en tête), le retire de la liste,
     * demande au DJActor de le charger, puis notifie le sender.
     */
    private void handleGetNextTrack(ActorContext ctx) {
        if (tracks.isEmpty()) {
            log.info("No more tracks in playlist for room {}", roomId);

            if (ctx.sender() != null) {
                ctx.sender().tell(Message.of("NO_TRACK", null), ctx.self());
            }
            return;
        }

        // Le premier élément correspond au meilleur choix après tri
        PlaylistTrack next = tracks.remove(0);

        log.info("Next track for room {}: trackId={}, title='{}'", roomId, next.getId(), next.getTitle());

        // Déclenche le chargement côté DJActor
        notifyDJActorLoadTrack(next, ctx);

        // Notifie l'acteur appelant
        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("NEXT_TRACK", new NextTrackMsg(next)),
                    ctx.self()
            );
        }
    }

    /**
     * Renvoie l'état de la playlist sous forme de snapshot.
     * Deux cas supportés :
     * 1) "ask local" via CompletableFuture passé en payload (complétion directe)
     * 2) échange acteur -> acteur via sender (tell d'un PLAYLIST_STATE)
     */
    private void handleGetPlaylist(Object payload, ActorContext ctx) {
        List<PlaylistTrack> snapshot = new ArrayList<>(tracks);
        PlaylistStateMsg stateMsg = new PlaylistStateMsg(snapshot);

        // Cas 1 : le payload est un future à compléter (pattern "ask" local)
        if (payload instanceof CompletableFuture<?> future) {
            @SuppressWarnings("unchecked")
            CompletableFuture<PlaylistStateMsg> typed = (CompletableFuture<PlaylistStateMsg>) future;
            typed.complete(stateMsg);
        }

        // Cas 2 : le sender attend une réponse classique via message
        if (ctx.sender() != null) {
            ctx.sender().tell(
                    Message.of("PLAYLIST_STATE", stateMsg),
                    ctx.self()
            );
        }
    }

    /**
     * Supprime un track par id, puis notifie succès/échec.
     */
    private void handleRemoveTrack(RemoveTrackMsg msg, ActorContext ctx) {
        boolean removed = tracks.removeIf(t -> t.getId() == msg.trackId);

        if (removed) {
            log.info("Track removed from room {}: trackId={}", roomId, msg.trackId);

            if (ctx.sender() != null) {
                ctx.sender().tell(Message.of("TRACK_REMOVED", msg.trackId), ctx.self());
            }
            return;
        }

        if (ctx.sender() != null) {
            ctx.sender().tell(Message.of("REMOVE_FAILED", "Track not found"), ctx.self());
        }
    }

    /**
     * Trie la playlist :
     * - score décroissant
     * - puis FIFO (ordre d'arrivée) en cas d'égalité
     */
    private void sortPlaylist() {
        tracks.sort(PlaylistTrack.BY_SCORE_DESC_THEN_FIFO);
    }

    /**
     * Demande au DJActor de charger un track.
     * En cas de problème de résolution / envoi, on loggue en error.
     */
    private void notifyDJActorLoadTrack(PlaylistTrack track, ActorContext ctx) {
        try {
            String targetPath = "djactor/dj-" + roomId;
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

            // Debug utile si tu veux diagnostiquer des problèmes de routage/selection
            log.debug("Sent LOAD_TRACK to DJActor '{}' for room {}", targetPath, roomId);
        } catch (Exception e) {
            log.error("Failed to notify DJActor for room " + roomId, e);
        }
    }

    /**
     * Log de cycle de vie : utile pour diagnostiquer l'arrêt de l'acteur.
     */
    @Override
    public void postStop(ActorContext ctx) {
        log.info("PlaylistActor stopped for room {}", roomId);
    }


    public record AddTrackMsg(String url, String title, long durationMs, String addedBy) {}
    public record VoteTrackMsg(long trackId, int delta) {}
    public record RemoveTrackMsg(long trackId) {}

    public record TrackAddedMsg(PlaylistTrack track) {}
    public record VoteSuccessMsg(long trackId, int newScore) {}
    public record NextTrackMsg(PlaylistTrack track) {}
    public record PlaylistStateMsg(List<PlaylistTrack> tracks) {}

    public record LoadTrackMsg(long id, String url, String title, long durationMs) {}
}
