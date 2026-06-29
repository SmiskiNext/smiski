/**
 * @type {import('@commitlint/types').UserConfig}
 */
const config = {
    extends: ['@commitlint/config-conventional'],
    rules: {
        'header-max-length': [2, 'always', 72],
        'body-leading-blank': [2, 'always'],
        'footer-leading-blank': [2, 'always'],
        'body-max-line-length': [2, 'always', 80],
        'footer-max-line-length': [2, 'always', 80],
        'signed-off-by': [2, 'always', 'Signed-off-by:']
    }
};

export default config;
