import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface ChatLine {
  userId: string;
  roomId: string;
  message: string;
  ts: number; // timestamp ms
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  constructor(private http: HttpClient) {}

  getHistory(roomId: string): Observable<ChatLine[]> {
    return this.http.get<ChatLine[]>(`/api/chat/${roomId}/history`);
  }

  send(roomId: string, userId: string, message: string): Observable<string> {
    return this.http.post(`/api/rooms/${roomId}/chat`, { userId, message }, { responseType: 'text' });
  }
}
