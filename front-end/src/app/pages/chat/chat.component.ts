import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { interval, Subject, switchMap, takeUntil } from 'rxjs';
import {ChatLine, ChatService} from '../../core/services/chat.service';
import {NgModule} from '@angular/core';
import {MaterialModule} from '../../shared/material.module';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, MaterialModule, DatePipe, FormsModule],
  templateUrl: './chat.component.html',


})
export class ChatComponent implements OnInit, OnDestroy {
  @Input({ required: true }) roomId!: string;
  @Input({ required: true }) userId!: string;

  lines: ChatLine[] = [];
  message = '';
  error: string | null = null;

  private destroy$ = new Subject<void>();

  constructor(private chat: ChatService) {}

  ngOnInit(): void {
    console.log('[CHAT] init', { roomId: this.roomId, userId: this.userId });

    interval(1000)
      .pipe(
        takeUntil(this.destroy$),
        switchMap(() => {
          console.log('[CHAT] polling...');
          return this.chat.getHistory(this.roomId);
        })
      )
      .subscribe({
        next: (data) => console.log('[CHAT] history', data),
        error: (err) => console.error('[CHAT] history error', err),
      });
  }


  send(): void {
    const text = this.message.trim();
    if (!text) return;

    this.chat.send(this.roomId, this.userId, text).subscribe({
      next: () => {
        this.message = '';
      },
      error: () => {
        this.error = "Impossible d'envoyer le message.";
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
