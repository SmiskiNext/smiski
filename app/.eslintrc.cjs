// ESLint config for the Forge app root (backend resolver, src/**/*.ts).
// The Custom UI frontend extends its own config at static/smiski-ui/.eslintrc.cjs.
module.exports = {
  root: true,
  env: {
    node: true,
    es2022: true,
  },
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
  plugins: ['@typescript-eslint'],
  extends: ['eslint:recommended', 'plugin:@typescript-eslint/recommended'],
  ignorePatterns: ['node_modules', 'dist', 'build', '.forge', 'static'],
  rules: {
    // Scaffold-friendly: stubs intentionally have unused params / empty bodies.
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    '@typescript-eslint/no-empty-function': 'off',
  },
};
