import { Component } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';

import { LookalikePage } from './lookalike/lookalike-page';

@Component({
  selector: 'app-root',
  imports: [LookalikePage, MatToolbarModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {}
