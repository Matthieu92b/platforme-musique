import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {forkJoin, map, Observable} from 'rxjs';
import {PlayerState} from '../models/player-state';
import {PlayerStatus} from '../models/player-status';

const API_BASE='http://localhost:8081/playerstate'
@Injectable({
  providedIn: 'root'
})
export class DjactorPlayerService {

  constructor(private http:HttpClient) { }

  addPlayerState(roomId:number):Observable<string>{
    return this.http.post(`${API_BASE}/add-player-state/${roomId}`, null, {
      responseType:'text'
    })
  }
  removePlayerState(roomId:number):Observable<void>{
    return this.http.post<void>(`${API_BASE}/remove-player-state/${roomId}`,null);
  }
  toggle(roomId:number):Observable<void>{
    return this.http.post<void>(`${API_BASE}/${roomId}/toggle`,null);
  }
  next(roomId:number):Observable<void>{
    return this.http.post<void>(`${API_BASE}/${roomId}/next`, null);
  }
  prev(roomId:number):Observable<void> {
    return this.http.post<void>(`${API_BASE}/${roomId}/prev`,null);
  }
  getStatus(roomId: number): Observable<PlayerStatus> {
    return this.http.get<PlayerStatus>(`${API_BASE}/${roomId}/status`);
  }
  getCurrentTrack(roomId:number):Observable<number>{
    return this.http.get<number>(`${API_BASE}/${roomId}/current-track`);
  }
  getCurrentPosition(roomId:number):Observable<number> {
    return this.http.get<number>(`${API_BASE}/${roomId}/current-position`);
  }
  getFullState(roomId:number):Observable<PlayerState> {
    return forkJoin({
      status:this.getStatus(roomId),
      currentTrackId:this.getCurrentTrack(roomId),
      positionMs:this.getCurrentPosition(roomId)
    }).pipe(
      map(res =>({roomId,...res}))
    );
  }
}
