import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';

@Component({
  selector: 'app-disclosure-dialog',
  imports: [MatButtonModule, MatDialogModule],
  templateUrl: './disclosure-dialog.html',
  styleUrl: './disclosure-dialog.scss'
})
export class DisclosureDialog {}
