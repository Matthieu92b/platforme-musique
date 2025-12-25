import { ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { interval, Subject, switchMap, takeUntil } from 'rxjs';
import { ChatLine, ChatService } from '../../core/services/chat.service';
import { MaterialModule } from '../../shared/material.module';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, MaterialModule, FormsModule],
  templateUrl: './chat.component.html',
})
export class ChatComponent implements OnInit, OnDestroy {
  @Input({ required: true }) roomId!: string;
  @Input({ required: true }) userId!: string;

  lines: ChatLine[] = [];
  message = '';
  error: string | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    private chat: ChatService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    console.log('[CHAT] init', { roomId: this.roomId, userId: this.userId });

    interval(1000)
      .pipe(
        takeUntil(this.destroy$),
        switchMap(() => this.chat.getHistory(this.roomId))
      )
      .subscribe({
        next: (data) => {
          console.log('[CHAT] history', data);

          this.lines = data ?? [];
          this.error = null;

          // utile si app "zoneless" ou updates hors zone
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('[CHAT] history error', err);
          this.error = "Impossible de récupérer l'historique du chat.";
          this.cdr.detectChanges();
        },
      });
  }

  send(): void {
    const text = this.message.trim();
    if (!text) return;

    this.chat.send(this.roomId, this.userId, text).subscribe({
      next: () => {
        this.message = '';
        // Optionnel: refresh immédiat (sinon tu attends max 1s)
        this.chat.getHistory(this.roomId).subscribe((data) => {
          this.lines = data ?? [];
          this.cdr.detectChanges();
        });
      },
      error: () => {
        this.error = "Impossible d'envoyer le message.";
        this.cdr.detectChanges();
      },
    });
  }

  trackByIdx(i: number): number {
    return i;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
