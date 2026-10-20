import { HttpErrorResponse } from '@angular/common/http';
import { apiErrorMessage } from './api-error';

describe('apiErrorMessage', () => {
  it('extracts the message field from a JSON error body', () => {
    const err = new HttpErrorResponse({ status: 400, error: { message: 'rolloutPercentage must be between 0 and 100' } });
    expect(apiErrorMessage(err, 'fallback')).toBe('rolloutPercentage must be between 0 and 100');
  });

  it('reports a connectivity problem for a status-0 error', () => {
    const err = new HttpErrorResponse({ status: 0 });
    expect(apiErrorMessage(err, 'fallback')).toContain('Could not reach toggleboard-api');
  });

  it('falls back when the error body has no message field', () => {
    const err = new HttpErrorResponse({ status: 500, error: { oops: true } });
    expect(apiErrorMessage(err, 'fallback')).toBe('fallback');
  });

  it('falls back for a non-HttpErrorResponse value', () => {
    expect(apiErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
  });
});
