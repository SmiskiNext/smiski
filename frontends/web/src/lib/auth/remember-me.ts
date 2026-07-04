const REMEMBER_ME_KEY = 'auth_remember_me';

export function getRememberMe(): boolean {
    if (typeof window === 'undefined') {
        return false;
    }
    return window.localStorage.getItem(REMEMBER_ME_KEY) === '1';
}

export function setRememberMe(value: boolean): void {
    if (typeof window === 'undefined') {
        return;
    }
    if (value) {
        window.localStorage.setItem(REMEMBER_ME_KEY, '1');
        return;
    }
    window.localStorage.removeItem(REMEMBER_ME_KEY);
}

export function clearRememberMe(): void {
    if (typeof window === 'undefined') {
        return;
    }
    window.localStorage.removeItem(REMEMBER_ME_KEY);
}
