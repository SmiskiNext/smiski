/**
 * Domain enums (string literal unions) for the Smiski meeting module.
 *
 * Types only — no runtime logic, no business rules. These mirror the backend
 * domain model and the BA report's state machine.
 */

/** Lifecycle status of a meeting. */
export type MeetingStatus = 'SCHEDULED' | 'RUNNING' | 'COMPLETED' | 'CANCELED';

/** Role a participant plays within a meeting. */
export type ParticipantRole = 'HOST' | 'PARTICIPANT';
