import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'flags', pathMatch: 'full' },
  {
    path: 'environments',
    loadComponent: () => import('./features/environments/environments-page.component').then((m) => m.EnvironmentsPageComponent),
  },
  {
    path: 'flags',
    loadComponent: () => import('./features/flags/flags-list.component').then((m) => m.FlagsListComponent),
  },
  {
    path: 'flags/new',
    loadComponent: () => import('./features/flags/flag-form.component').then((m) => m.FlagFormComponent),
  },
  {
    path: 'flags/:flagKey',
    loadComponent: () => import('./features/flags/flag-form.component').then((m) => m.FlagFormComponent),
  },
  {
    path: 'segments',
    loadComponent: () => import('./features/segments/segments-list.component').then((m) => m.SegmentsListComponent),
  },
  {
    path: 'segments/new',
    loadComponent: () => import('./features/segments/segment-form.component').then((m) => m.SegmentFormComponent),
  },
  {
    path: 'segments/:segmentKey',
    loadComponent: () => import('./features/segments/segment-form.component').then((m) => m.SegmentFormComponent),
  },
  {
    path: 'audit',
    loadComponent: () => import('./features/audit/audit-log.component').then((m) => m.AuditLogComponent),
  },
  { path: '**', redirectTo: 'flags' },
];
