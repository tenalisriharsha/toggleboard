import { HttpErrorResponse } from '@angular/common/http';

/**
 * Every error the API returns (validation, 404/409, the auth filters'
 * rejections) is JSON with a `message` field — see GlobalExceptionHandler
 * and the two auth filters on the server side.
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error;
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      return body.message;
    }
    if (err.status === 0) {
      return 'Could not reach toggleboard-api. Is it running?';
    }
  }
  return fallback;
}
