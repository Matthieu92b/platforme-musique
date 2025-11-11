import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {DjactorModule} from './features/djactor/djactor.module';
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, DjactorModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'music-platform';
}
