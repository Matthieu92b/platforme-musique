import {PlayerStatus} from './player-status';

export interface PlayerState {
  roomId:number;
  status:PlayerStatus;
  currentTrackId:number;
  positionMs:number;
}
