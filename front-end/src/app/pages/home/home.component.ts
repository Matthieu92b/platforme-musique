import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { RoomApiService } from '../../core/services/room-api.service';
import { CreateRoomRequest, CreateRoomResponse } from '../../core/models/room.models';
import { MaterialModule } from '../../shared/material.module';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, MaterialModule],
  templateUrl: './home.component.html',
})
export class HomeComponent {
  userId = '';
  roomName = '';

  joinRoomId = '';

  isLoadingCreate = false;
  isLoadingJoin = false;
  error: string | null = null;

  constructor(
    private readonly roomApi: RoomApiService,
    private readonly router: Router
  ) {
    // On peut récupérer un userId déjà stocké
    const storedUser = localStorage.getItem('userId');
    if (storedUser) {
      this.userId = storedUser;
    }
  }

  createRoom(): void {
    this.error = null;

    if (!this.userId) {
      this.error = 'User ID obligatoire pour créer une room.';
      return;
    }

    const payload: CreateRoomRequest = {
      userId: this.userId,
      roomName: this.roomName || undefined,
    };

    this.isLoadingCreate = true;

    this.roomApi.createRoom(payload).subscribe({
      next: (res: CreateRoomResponse) => {
        localStorage.setItem('userId', res.userId);
        this.isLoadingCreate = false;
        this.router.navigate(['/room', res.roomId]);
      },
      error: (err: unknown) => {
        console.error('Erreur create room', err);
        this.error = 'Erreur lors de la création de la room.';
        this.isLoadingCreate = false;
      }
    });
  }

  joinRoom(): void {
    this.error = null;

    if (!this.userId) {
      this.error = 'User ID obligatoire pour rejoindre une room.';
      return;
    }
    if (!this.joinRoomId) {
      this.error = 'Room ID obligatoire pour rejoindre.';
      return;
    }

    this.isLoadingJoin = true;

    this.roomApi.joinRoom(this.joinRoomId, { userId: this.userId }).subscribe({
      next: () => {
        localStorage.setItem('userId', this.userId);
        this.isLoadingJoin = false;
        this.router.navigate(['/room', this.joinRoomId]);
      },
      error: (err) => {
        console.error('Erreur join room', err);
        this.error = 'Erreur lors de la connexion à la room.';
        this.isLoadingJoin = false;
      }
    });
  }
}
