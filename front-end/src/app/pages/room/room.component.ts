// src/app/pages/room/room.component.ts
import {
  Component,
  OnInit,
  OnDestroy,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, timer, switchMap } from 'rxjs';

import { RoomApiService } from '../../core/services/room-api.service';
import { AddTrackRequest, PlayerState } from '../../core/models/room.models';
import { MaterialModule } from '../../shared/material.module';
import { PlaylistTrackDto } from '../../core/models/playlist.models';
import { ChatComponent } from '../chat/chat.component';

@Component({
  selector: 'app-room',
  standalone: true,
  imports: [CommonModule, FormsModule, MaterialModule, ChatComponent],
  templateUrl: './room.component.html',
})
export class RoomComponent implements OnInit, OnDestroy {
  roomId = '';
  userId = '';

  trackTitle = '';
  trackUrl = '';
  durationMs: number | null = 180_000;

  isAdding = false;
  isLoadingPlaylist = false;
  isLoadingPlayerState = false;
  error: string | null = null;

  playlist: PlaylistTrackDto[] = [];
  playerState: PlayerState | null = null;

  @ViewChild('audioPlayer') audioPlayerRef?: ElementRef<HTMLAudioElement>;
  private lastAppliedAudioUrl: string | null = null;

  displayPositionMs = 0;
  displayDurationMs = 0;

  private ignoreBackendUntil = 0;
  private readonly ACTION_GRACE_MS = 400;

  private audioListenersAttached = false;

  private pendingSeekMs: number | null = null;
  private pendingAutoPlay = false;

  private lastStateReceivedAtMs = 0;
  private readonly SEEK_TOLERANCE_MS = 500;

  private subs: Subscription[] = [];
  private playerPollingSub?: Subscription;
  private playlistPollingSub?: Subscription;

  private onTimeUpdate = () => {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio) return;

    this.displayPositionMs = Math.floor(audio.currentTime * 1000);

    if (Number.isFinite(audio.duration) && audio.duration > 0) {
      this.displayDurationMs = Math.floor(audio.duration * 1000);
    }
  };

  private onLoadedMetadata = () => {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio) return;

    if (Number.isFinite(audio.duration) && audio.duration > 0) {
      this.displayDurationMs = Math.floor(audio.duration * 1000);
    }

    if (this.pendingSeekMs != null) {
      audio.currentTime = this.pendingSeekMs / 1000;
      this.displayPositionMs = this.pendingSeekMs;
      this.pendingSeekMs = null;
    }
  };

  private onCanPlay = () => {
    const audio = this.audioPlayerRef?.nativeElement;
    if (!audio) return;

    if (this.pendingAutoPlay) {
      this.pendingAutoPlay = false;
      audio.play().catch(() => {});
    }
  };

  constructor(
    private readonly route: ActivatedRoute,
    private readonly roomApi: RoomApiService
  ) {}

  ngOnInit(): void {
    this.roomId = this.route.snapshot.paramMap.get('roomId') ?? '';
    this.userId = localStorage.getItem('userId') ?? '';

    if (!this.roomId) {
      this.error = 'Aucun roomId trouvé dans l’URL';
      return;
    }
    if (!this.userId) this.userId = 'anonymous';

    this.loadPlaylistOnce();
    this.loadPlayerStateOnce();

    this.playerPollingSub = timer(0, 1000)
      .pipe(switchMap(() => this.roomApi.getPlayerState(this.roomId)))
      .subscribe({
        next: (state) => this.applyBackendState(state),
        error: () => {},
      });

    this.playlistPollingSub = timer(0, 2000)
      .pipe(switchMap(() => this.roomApi.getPlaylist(this.roomId)))
      .subscribe({
        next: (state) => (this.playlist = state?.tracks ?? []),
        error: () => {},
      });
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
    this.playerPollingSub?.unsubscribe();
    this.playlistPollingSub?.unsubscribe();

    const audio = this.audioPlayerRef?.nativeElement;
    if (audio && this.audioListenersAttached) {
      audio.removeEventListener('timeupdate', this.onTimeUpdate);
      audio.removeEventListener('loadedmetadata', this.onLoadedMetadata);
      audio.removeEventListener('canplay', this.onCanPlay);
    }
  }

  loadPlaylistOnce(): void {
    this.isLoadingPlaylist = true;
    const sub = this.roomApi.getPlaylist(this.roomId).subscribe({
      next: (state) => {
        this.playlist = state?.tracks ?? [];
        this.isLoadingPlaylist = false;
      },
      error: (err) => {
        console.error('Erreur getPlaylist', err);
        this.isLoadingPlaylist = false;
      },
    });
    this.subs.push(sub);
  }

  loadPlayerStateOnce(): void {
    this.isLoadingPlayerState = true;
    const sub = this.roomApi.getPlayerState(this.roomId).subscribe({
      next: (state) => {
        this.isLoadingPlayerState = false;
        this.applyBackendState(state);
      },
      error: (err) => {
        console.error('Erreur getPlayerState', err);
        this.isLoadingPlayerState = false;
      },
    });
    this.subs.push(sub);
  }

  addTrack(): void {
    if (!this.trackTitle || !this.trackUrl) {
      this.error = 'Titre et URL sont obligatoires';
      return;
    }

    this.isAdding = true;
    this.error = null;

    const payload: AddTrackRequest = {
      userId: this.userId,
      trackTitle: this.trackTitle,
      trackUrl: this.trackUrl,
      durationMs: this.durationMs ?? 180_000,
    };

    const sub = this.roomApi.addTrack(this.roomId, payload).subscribe({
      next: () => {
        this.isAdding = false;
        this.trackTitle = '';
        this.trackUrl = '';
        this.durationMs = 180_000;
        this.loadPlaylistOnce();
      },
      error: (err) => {
        console.error('Erreur addTrack', err);
        this.isAdding = false;
        this.error = "Erreur lors de l'ajout du morceau";
      },
    });

    this.subs.push(sub);
  }

  play(): void {
    this.ignoreBackendUntil = Date.now() + this.ACTION_GRACE_MS;

    const audio = this.audioPlayerRef?.nativeElement;
    if (audio && audio.paused) {
      audio.play().catch(() => {});
    }

    const sub = this.roomApi.play(this.roomId).subscribe({
      next: () => {},
      error: (err) => console.error('Erreur play (backend)', err),
    });
    this.subs.push(sub);
  }

  pause(): void {
    this.ignoreBackendUntil = Date.now() + this.ACTION_GRACE_MS;

    const audio = this.audioPlayerRef?.nativeElement;
    if (audio && !audio.paused) {
      audio.pause();
    }

    const sub = this.roomApi.pause(this.roomId).subscribe({
      next: () => {},
      error: (err) => console.error('Erreur pause (backend)', err),
    });
    this.subs.push(sub);
  }

  next(): void {
    if (!this.canGoNext()) return;

    this.displayPositionMs = 0;
    this.displayDurationMs = 0;

    const sub = this.roomApi.next(this.roomId).subscribe({
      next: () => {
        this.loadPlaylistOnce();
        this.loadPlayerStateOnce();
      },
      error: (err) => console.error('Erreur next (backend)', err),
    });
    this.subs.push(sub);
  }

  canGoNext(): boolean {
    if (!this.playlist || this.playlist.length < 1) return false;
    if (!this.playerState?.currentUrl) return false;
    return true;
  }

  private applyBackendState(state: PlayerState): void {
    this.playerState = state;
    this.lastStateReceivedAtMs = Date.now();

    if (state.durationMs && state.durationMs > 0) {
      if (!this.displayDurationMs || this.displayDurationMs <= 0) {
        this.displayDurationMs = state.durationMs;
      }
    }

    this.applyStateToAudio();
  }

  private computeTargetPositionMs(state: PlayerState): number {
    const base = state.positionMs ?? 0;
    const status = (state.status ?? '').toUpperCase();

    if (status !== 'PLAYING') return Math.max(0, base);

    const now = Date.now();
    const receivedAt = this.lastStateReceivedAtMs || now;
    const elapsed = now - receivedAt;

    return Math.max(0, base + elapsed);
  }

  private attachAudioListenersOnce(audio: HTMLAudioElement): void {
    if (this.audioListenersAttached) return;

    audio.addEventListener('timeupdate', this.onTimeUpdate);
    audio.addEventListener('loadedmetadata', this.onLoadedMetadata);
    audio.addEventListener('canplay', this.onCanPlay);

    this.audioListenersAttached = true;
  }

  private applyStateToAudio(): void {
    const audio = this.audioPlayerRef?.nativeElement;
    const state = this.playerState;
    if (!audio || !state) return;

    this.attachAudioListenersOnce(audio);

    const url = state.currentUrl;
    const status = (state.status ?? '').toUpperCase();
    const targetPosMs = this.computeTargetPositionMs(state);

    if (url && url !== this.lastAppliedAudioUrl) {
      this.lastAppliedAudioUrl = url;

      audio.pause();
      audio.src = url;

      this.pendingSeekMs = targetPosMs;
      this.pendingAutoPlay = status === 'PLAYING';

      audio.load();

      this.displayPositionMs = targetPosMs;
      this.displayDurationMs = 0;
    } else if (url) {
      const currentMs = Math.floor(audio.currentTime * 1000);
      if (Math.abs(currentMs - targetPosMs) > this.SEEK_TOLERANCE_MS) {
        audio.currentTime = targetPosMs / 1000;
        this.displayPositionMs = targetPosMs;
      }
    }

    if (Date.now() < this.ignoreBackendUntil) return;

    if (this.pendingAutoPlay) return;

    if (status === 'PLAYING') {
      if (audio.paused) audio.play().catch(() => {});
    } else if (status === 'PAUSED' || status === 'STOPPED') {
      if (!audio.paused) audio.pause();
    }
  }

  formatTime(ms: number | null | undefined): string {
    if (!ms || ms <= 0) return '0:00';
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = (totalSeconds % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
  }

  getCurrentTrackLabel(): string {
    return this.playerState?.currentTitle ?? 'Aucun';
  }

  getProgressPercent(): number {
    const dur = this.displayDurationMs || this.playerState?.durationMs || 0;
    if (!dur || dur <= 0) return 0;

    const pct = (this.displayPositionMs / dur) * 100;
    if (!Number.isFinite(pct)) return 0;

    return Math.min(100, Math.max(0, pct));
  }
}
