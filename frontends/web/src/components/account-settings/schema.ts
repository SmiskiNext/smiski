import { z } from 'zod';

export const accountProfileSchema = z.object({
    fullName: z.string().min(1).max(255, 'validation_fullName_too_long'),
    username: z
        .string()
        .min(3, 'validation_username_too_short')
        .max(30, 'validation_username_too_long')
        .regex(/^[a-zA-Z0-9_-]+$/, 'validation_username_invalid_chars'),
    avatarUrl: z
        .string()
        .max(2048, 'validation_avatarUrl_too_long')
        .optional()
        .or(z.literal('')),
});

export type AccountProfileFormValues = z.infer<typeof accountProfileSchema>;

export const AVATAR_MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
export const AVATAR_ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
