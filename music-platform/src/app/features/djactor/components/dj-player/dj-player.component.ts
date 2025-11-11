import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {PlayerState} from '../../models/player-state';
import {interval, Subscription, switchMap} from 'rxjs';
import {DjactorPlayerService} from '../../services/djactor-player.service';

@Component({
  selector: 'app-dj-player',
  templateUrl: './dj-player.component.html',
  styleUrl: './dj-player.component.scss'
})
export class DjPlayerComponent implements OnInit , OnDestroy{
  roomId=1;
  state:PlayerState | null=null;
  loading=false;
  error:string | null=null;

  private pollSub?: Subscription;
  private djService=inject(DjactorPlayerService);

  ngOnInit() {
    this.loadState();

    this.pollSub=interval(2000).pipe(
      switchMap(() => this.djService.getFullState(this.roomId))
    ).subscribe({
      next:(st) => this.state=st,
      error:()=>{}
    });
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  loadState():void{
    this.loading=true;
    this.error=null;
    this.djService.getFullState(this.roomId).subscribe({
        next: (st) => {
          this.state = st;
          this.loading=false;
        },
      error:()=> {
          this.error='Impossible de charger le player de cette room'
        this.state=null;
          this.loading=false;
      }
      }
    );

  }
  addPlayer():void{
    this.djService.getFullState(this.roomId).subscribe({
      next:()=> this.loadState()
    });
  }
  removePlayer():void {
    this.djService.removePlayerState(this.roomId).subscribe({
      next:()=>this.loadState()
    })
  }
  toggle():void{
    this.djService.toggle(this.roomId).subscribe({
      next:()=>this.loadState()
    });
  }
  next(): void {
    this.djService.next(this.roomId).subscribe({
      next:()=>this.loadState()
    });
  }
  prev():void{
    this.djService.prev(this.roomId).subscribe({
      next:()=>this.loadState()
    });
  }

}
