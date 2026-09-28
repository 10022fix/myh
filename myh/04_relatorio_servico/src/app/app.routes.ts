import { Routes } from '@angular/router';
import { RelatorioComponent } from './relatorio/relatorio.component';

export const routes: Routes = [
  { path: '', component: RelatorioComponent },
  { path: '**', redirectTo: '' }
];
