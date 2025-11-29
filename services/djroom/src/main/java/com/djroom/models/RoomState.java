package com.djroom.models;

import java.io.Serializable;
import java.util.List;

/**
 * Represents the state of a room
 */
public class RoomState implements Serializable {

    private final String roomId;
    private final List<String> members;
    private final String hostUserId;
    private final RoomStatus status;
    private final int playlistSize;

    public RoomState(String roomId, List<String> members, String hostUserId,
                     RoomStatus status, int playlistSize) {
        this.roomId = roomId;
        this.members = members;
        this.hostUserId = hostUserId;
        this.status = status;
        this.playlistSize = playlistSize;
    }

    // Getters
    public String getRoomId() { return roomId; }
    public List<String> getMembers() { return members; }
    public String getHostUserId() { return hostUserId; }
    public RoomStatus getStatus() { return status; }
    public int getPlaylistSize() { return playlistSize; }

    public enum RoomStatus {
        ACTIVE, PAUSED, CLOSED
    }
}