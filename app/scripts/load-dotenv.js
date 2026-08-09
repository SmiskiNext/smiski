const fs = require('node:fs');
const path = require('node:path');

/**
 * Loads a simple dotenv file without overriding values already supplied by the
 * caller's environment. Deployment/CI variables must always win over local
 * developer defaults.
 */
function loadDotEnv(target = process.env, cwd = process.cwd()) {
    const dotenvPath = path.resolve(cwd, '.env');
    if (!fs.existsSync(dotenvPath)) return target;

    for (const line of fs.readFileSync(dotenvPath, 'utf-8').split(/\r?\n/)) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) {
            continue;
        }

        const equalsIndex = trimmed.indexOf('=');
        const key = trimmed.slice(0, equalsIndex).trim();
        if (!key || target[key] !== undefined) continue;

        let value = trimmed.slice(equalsIndex + 1).trim();
        if (
            (value.startsWith('"') && value.endsWith('"'))
            || (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.slice(1, -1);
        }
        target[key] = value;
    }

    return target;
}

module.exports = { loadDotEnv };
