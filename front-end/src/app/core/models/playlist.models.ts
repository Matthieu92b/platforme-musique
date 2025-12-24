// src/app/core/models/playlist.models.ts
export interface PlaylistTrackDto {
  id: number;
  url: string;
  title: string;
  durationMs: number;
  score: number;
  addedAt: string;
  addedBy: string;
}

export interface PlaylistStateDto {
  tracks: PlaylistTrackDto[];
}
