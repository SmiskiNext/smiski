## ADDED Requirements

### Requirement: K8s production secrets for reCAPTCHA

The production K8s overlay SHALL include `recaptcha-secret-key` in the
`user-management-secrets` Secret resource.

#### Scenario: Secret available in production overlay

- **WHEN** the production overlay is applied
- **THEN** `services/k8s/overlays/prod/secrets/user-management-secrets.yaml`
  contains a `recaptcha-secret-key` entry with placeholder value

#### Scenario: Secret available in staging overlay

- **WHEN** the staging overlay is applied
- **THEN** `services/k8s/overlays/staging/secrets/user-management-secrets.yaml`
  contains a `recaptcha-secret-key` entry with placeholder value

### Requirement: K8s deployment environment variables

The `user-management` Deployment SHALL include `RECAPTCHA_ENABLED`,
`RECAPTCHA_SITE_KEY`, and `RECAPTCHA_SECRET_KEY` environment variables.

#### Scenario: Deployment includes reCAPTCHA env vars

- **WHEN** the user-management deployment manifest is applied
- **THEN** the container spec includes:
    - `RECAPTCHA_ENABLED` with a configurable value (default `false`)
    - `RECAPTCHA_SITE_KEY` with a configurable value
    - `RECAPTCHA_SECRET_KEY` sourced from the `user-management-secrets` Secret

#### Scenario: Default disabled state

- **WHEN** `RECAPTCHA_ENABLED` is not explicitly set or set to `false`
- **THEN** the backend uses `NoOpCaptchaVerifier` (accepts any token)

### Requirement: Docker dev environment variables

The Docker dev `.env.example` SHALL include reCAPTCHA configuration variables
with sensible defaults.

#### Scenario: .env.example includes reCAPTCHA vars

- **WHEN** a developer copies `.env.example` to `.env`
- **THEN** the file contains `RECAPTCHA_ENABLED=false`, `RECAPTCHA_SITE_KEY=`,
  and `RECAPTCHA_SECRET_KEY=` with comments explaining their purpose

### Requirement: Web environment variable documentation

The web app `.env.local.example` SHALL include `NEXT_PUBLIC_RECAPTCHA_SITE_KEY`
with documentation.

#### Scenario: .env.local.example includes reCAPTCHA site key

- **WHEN** a developer copies `.env.local.example` to `.env.local`
- **THEN** the file contains `NEXT_PUBLIC_RECAPTCHA_SITE_KEY=` with a comment
  explaining it is the Google reCAPTCHA v3 site key for the forgot-password flow
