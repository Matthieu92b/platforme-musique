// src/app/core/models/room.models.ts

export interface CreateRoomRequest {
  userId: string;
  roomName?: string;
}

export interface CreateRoomResponse {
  roomId: string;
  userId: string;
  roomName: string;
}

export interface JoinRoomRequest {
  userId: string;
}

export interface AddTrackRequest {
  userId: string;
  trackUrl: string;
  trackTitle: string;
  durationMs: number;
}

export interface VoteTrackRequest {
  userId: string;
  delta: number; // +1 / -1
}

export interface ChatMessageRequest {
  userId: string;
  message: string;
}
