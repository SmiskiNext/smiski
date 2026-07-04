import { describe, expect, it } from 'vitest';
import {
    buildInitialStepSchema,
    buildPasswordStepSchema,
} from './join-form.tsx';

const REQUIRED_MSG = 'This field is required';

describe('buildInitialStepSchema', () => {
    const schema = buildInitialStepSchema(REQUIRED_MSG);

    it('passes when code and displayName are non-empty', () => {
        const result = schema.safeParse({
            code: 'ABC123',
            displayName: 'Alice',
        });
        expect(result.success).toBe(true);
    });

    it('fails when code is empty', () => {
        const result = schema.safeParse({ code: '', displayName: 'Alice' });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues[0].message).toBe(REQUIRED_MSG);
        }
    });

    it('fails when displayName is empty', () => {
        const result = schema.safeParse({ code: 'ABC123', displayName: '' });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues[0].message).toBe(REQUIRED_MSG);
        }
    });

    it('fails when both code and displayName are empty', () => {
        const result = schema.safeParse({ code: '', displayName: '' });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues).toHaveLength(2);
        }
    });

    it('passes when code contains surrounding whitespace (schema does not trim)', () => {
        const result = schema.safeParse({
            code: '   ABC123   ',
            displayName: '   Alice   ',
        });
        expect(result.success).toBe(true);
    });

    it('uses the provided required message as the error message', () => {
        const customMsg = 'custom required';
        const customSchema = buildInitialStepSchema(customMsg);
        const result = customSchema.safeParse({ code: '', displayName: 'X' });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues[0].message).toBe(customMsg);
        }
    });
});

describe('buildPasswordStepSchema', () => {
    const schema = buildPasswordStepSchema(REQUIRED_MSG);

    it('passes when displayName and password are non-empty', () => {
        const result = schema.safeParse({
            displayName: 'Bob',
            password: 'secret',
        });
        expect(result.success).toBe(true);
    });

    it('fails when password is empty', () => {
        const result = schema.safeParse({ displayName: 'Bob', password: '' });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues[0].message).toBe(REQUIRED_MSG);
        }
    });

    it('fails when displayName is empty', () => {
        const result = schema.safeParse({
            displayName: '',
            password: 'secret',
        });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues[0].message).toBe(REQUIRED_MSG);
        }
    });

    it('fails when both fields are empty', () => {
        const result = schema.safeParse({ displayName: '', password: '' });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues).toHaveLength(2);
        }
    });

    it('uses the provided required message as the error message', () => {
        const customMsg = 'custom required';
        const customSchema = buildPasswordStepSchema(customMsg);
        const result = customSchema.safeParse({
            displayName: '',
            password: 'x',
        });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues[0].message).toBe(customMsg);
        }
    });
});

describe('schema error messages are i18n-compatible', () => {
    it('error message is a non-empty string', () => {
        const schema = buildInitialStepSchema(REQUIRED_MSG);
        const result = schema.safeParse({ code: '', displayName: '' });
        expect(result.success).toBe(false);
        if (!result.success) {
            const msg = result.error.issues[0].message;
            expect(typeof msg).toBe('string');
            expect(msg.length).toBeGreaterThan(0);
        }
    });

    it('the schema accepts any translated string as the required message', () => {
        const viRequired = 'Trường này là bắt buộc';
        const viSchema = buildInitialStepSchema(viRequired);
        const result = viSchema.safeParse({ code: '', displayName: 'X' });
        expect(result.success).toBe(false);
        if (!result.success) {
            expect(result.error.issues[0].message).toBe(viRequired);
        }
    });
});
