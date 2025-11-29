// src/app/core/models/playlist.models.ts
export interface PlaylistTrack {
  id: number;
  url: string;
  title: string;
  durationMs: number;
  score: number;
  addedAt: string;   // Instant → string ISO
  addedBy: string;
}
