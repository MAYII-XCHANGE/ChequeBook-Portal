import { describe, expect, it } from 'vitest';
import { PROCESSING_QUEUE_STATUSES, PROCESSING_TRANSITIONS, REQUEST_STATUS } from './status';

describe('processing lifecycle', () => {
  it('ends Clearing Operations processing when a request is dispatched', () => {
    expect(PROCESSING_TRANSITIONS[REQUEST_STATUS.FINACLE_REGISTERED].value).toBe(REQUEST_STATUS.DISPATCHED);
    expect(PROCESSING_TRANSITIONS[REQUEST_STATUS.PRINTED].value).toBe(REQUEST_STATUS.DISPATCHED);
    expect(PROCESSING_TRANSITIONS[REQUEST_STATUS.DISPATCHED]).toBeUndefined();
    expect(PROCESSING_QUEUE_STATUSES).not.toContain(REQUEST_STATUS.DISPATCHED);
  });

  it('requires vendor printing to be confirmed from the export batch', () => {
    expect(PROCESSING_TRANSITIONS[REQUEST_STATUS.SENT_TO_VENDOR]).toBeUndefined();
    expect(PROCESSING_QUEUE_STATUSES).not.toContain(REQUEST_STATUS.SENT_TO_VENDOR);
  });

  it('leaves customer delivery outside the Clearing Operations queue', () => {
    expect(PROCESSING_TRANSITIONS[REQUEST_STATUS.RECEIVED_BY_BRANCH]).toBeUndefined();
    expect(PROCESSING_QUEUE_STATUSES).not.toContain(REQUEST_STATUS.RECEIVED_BY_BRANCH);
  });

  it('keeps failed debit states outside the Clearing Operations queue', () => {
    expect(PROCESSING_QUEUE_STATUSES).not.toContain(REQUEST_STATUS.DEBIT_FAILED);
    expect(PROCESSING_QUEUE_STATUSES).not.toContain(REQUEST_STATUS.DEBIT_REVIEW_REQUIRED);
    expect(PROCESSING_QUEUE_STATUSES).not.toContain(REQUEST_STATUS.CHEQUEBOOK_REQUEST_FAILED);
    expect(PROCESSING_QUEUE_STATUSES).not.toContain(REQUEST_STATUS.REVERSAL_REVIEW_REQUIRED);
  });
});
