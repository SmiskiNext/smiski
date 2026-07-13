import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

export const REPO_ROOT = resolve(here, '..', '..', '..');

export const DOCKER_DIR = resolve(REPO_ROOT, 'services', 'docker');
export const DOCKER_ENV = resolve(DOCKER_DIR, '.env');
export const DOCKER_ENV_EXAMPLE = resolve(DOCKER_DIR, '.env.example');

export const SERVICES_DIR = resolve(REPO_ROOT, 'services');
export const GRADLEW = resolve(SERVICES_DIR, 'gradlew');

export const BACKEND_SERVICES = [
    'user-management',
    'meeting-management',
    'chat-management',
    'notification',
] as const;

export type BackendService = (typeof BACKEND_SERVICES)[number];
