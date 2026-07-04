import { describe, expect, it } from 'vitest';
import {
    AVATAR_ALLOWED_TYPES,
    AVATAR_MAX_SIZE_BYTES,
    accountProfileSchema,
} from './schema.ts';

describe('accountProfileSchema', () => {
    describe('fullName', () => {
        it('accepts a valid full name', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex Nguyen',
                username: 'alex_nguyen',
            });
            expect(result.success).toBe(true);
        });

        it('rejects an empty full name', () => {
            const result = accountProfileSchema.safeParse({
                fullName: '',
                username: 'alex_nguyen',
            });
            expect(result.success).toBe(false);
        });

        it('rejects a full name exceeding 255 characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'A'.repeat(256),
                username: 'alex_nguyen',
            });
            expect(result.success).toBe(false);
        });

        it('accepts a full name of exactly 255 characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'A'.repeat(255),
                username: 'alex_nguyen',
            });
            expect(result.success).toBe(true);
        });
    });

    describe('username', () => {
        it('accepts a valid username', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'alex_nguyen',
            });
            expect(result.success).toBe(true);
        });

        it('rejects a username shorter than 3 characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'ax',
            });
            expect(result.success).toBe(false);
        });

        it('accepts a username of exactly 3 characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'axe',
            });
            expect(result.success).toBe(true);
        });

        it('rejects a username longer than 30 characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'a'.repeat(31),
            });
            expect(result.success).toBe(false);
        });

        it('accepts a username of exactly 30 characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'a'.repeat(30),
            });
            expect(result.success).toBe(true);
        });

        it('rejects a username with invalid characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'alex nguyen!',
            });
            expect(result.success).toBe(false);
        });

        it('accepts a username with allowed characters: letters, digits, underscore, hyphen', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'Alex-Nguyen_123',
            });
            expect(result.success).toBe(true);
        });
    });

    describe('avatarUrl', () => {
        it('accepts absence of avatarUrl', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'alex',
            });
            expect(result.success).toBe(true);
        });

        it('accepts an empty avatarUrl string', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'alex',
                avatarUrl: '',
            });
            expect(result.success).toBe(true);
        });

        it('rejects an avatarUrl exceeding 2048 characters', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'alex',
                avatarUrl: `https://example.com/${'a'.repeat(2040)}`,
            });
            expect(result.success).toBe(false);
        });

        it('accepts a valid avatar URL', () => {
            const result = accountProfileSchema.safeParse({
                fullName: 'Alex',
                username: 'alex',
                avatarUrl: 'https://example.com/avatar.jpg',
            });
            expect(result.success).toBe(true);
        });
    });
});

describe('avatar file validation constants', () => {
    it('allows supported MIME types', () => {
        expect(AVATAR_ALLOWED_TYPES).toContain('image/jpeg');
        expect(AVATAR_ALLOWED_TYPES).toContain('image/png');
        expect(AVATAR_ALLOWED_TYPES).toContain('image/webp');
    });

    it('does not allow unsupported MIME types', () => {
        expect(AVATAR_ALLOWED_TYPES).not.toContain('image/gif');
        expect(AVATAR_ALLOWED_TYPES).not.toContain('application/pdf');
    });

    it('enforces a 5 MB maximum file size', () => {
        expect(AVATAR_MAX_SIZE_BYTES).toBe(5 * 1024 * 1024);
    });
});
