import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { EnvironmentContextService } from './environment-context.service';

/**
 * The console only ever talks to the admin CRUD endpoints (never the
 * client-SDK data-plane), so every mutating request gets the admin role and
 * the currently-configured actor name attached here, rather than every
 * call site repeating the same two headers.
 */
export const adminHeadersInterceptor: HttpInterceptorFn = (req, next) => {
  const context = inject(EnvironmentContextService);

  if (req.method === 'GET') {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: {
        'X-Role': 'admin',
        'X-Actor': context.actor(),
      },
    }),
  );
};
