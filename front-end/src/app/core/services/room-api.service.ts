// src/app/core/services/room-api.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  CreateRoomRequest,
  CreateRoomResponse,
  JoinRoomRequest,
  AddTrackRequest,
  VoteTrackRequest,
  ChatMessageRequest
} from '../models/room.models';
import { Observable } from 'rxjs';
import { PlaylistStateDto } from '../models/playlist.models';
import { PlayerState } from '../models/room.models';
@Injectable({ providedIn: 'root' })
export class RoomApiService {
  // Avec proxy Angular : /api → http://localhost:8082
  private readonly baseUrl = '/api/rooms';
  // Si tu ne veux pas de proxy : private readonly baseUrl = 'http://localhost:8082/api/rooms';
  private readonly playerBaseUrl = '/api/player';
  constructor(private http: HttpClient) {}

  // ---------- CREATE ROOM : renvoie un vrai JSON ----------
  createRoom(req: CreateRoomRequest): Observable<CreateRoomResponse> {
    return this.http.post<CreateRoomResponse>(this.baseUrl, req);
  }

  // ---------- JOIN / LEAVE ----------
  joinRoom(roomId: string, req: JoinRoomRequest): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/join`,
      req,
      { responseType: 'text' as 'json' }
    );
  }

  leaveRoom(roomId: string, userId: string): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/leave`,
      { userId },
      { responseType: 'text' as 'json' }
    );
  }

  // ---------- TRACKS ----------
  addTrack(roomId: string, req: AddTrackRequest): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/tracks`,
      req,
      { responseType: 'text' as 'json' }
    );
  }

  voteTrack(roomId: string, trackId: number, req: VoteTrackRequest): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/tracks/${trackId}/vote`,
      req,
      { responseType: 'text' as 'json' }
    );
  }

  // ---------- PLAYER ----------
  play(roomId: string): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/play`,
      {},
      { responseType: 'text' as 'json' }
    );
  }

  pause(roomId: string): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/pause`,
      {},
      { responseType: 'text' as 'json' }
    );
  }

  next(roomId: string): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/next`,
      {},
      { responseType: 'text' as 'json' }
    );
  }

  // ---------- CHAT ----------
  sendChat(roomId: string, req: ChatMessageRequest): Observable<string> {
    return this.http.post<string>(
      `${this.baseUrl}/${roomId}/chat`,
      req,
      { responseType: 'text' as 'json' }
    );
  }

  // ---------- PLAYLIST (pour l’instant texte aussi) ----------
  getPlaylist(roomId: string): Observable<PlaylistStateDto> {
    return this.http.get<PlaylistStateDto>(`${this.baseUrl}/${roomId}/playlist`);
  }
  getPlayerState(roomId: string) {
    return this.http.get<PlayerState>(`${this.playerBaseUrl}/${roomId}/state`);
  }
}
