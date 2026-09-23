# Cognito Hosted UI branding (classic)

Hive uses **Hosted UI (classic)** on the Cognito domain (`managed_login_version = 1`).

That is the white card / email+password / blue Sign in layout. Logo and colors can be customized later — **not** via Managed login style editor.

## Customize later (logo + colors)

1. Cognito → User pool `portal26-hive-auth-poc` → confirm Domain **Branding version = Hosted UI (classic)**.
2. App clients → **`portal26-hive-backend`** → Hosted UI / **UI customization** (classic), or use API `SetUICustomization`.
3. Upload **logo** (portal26 + Hive header image recommended).
4. Optional **CSS** for header/button colors (e.g. dark charcoal `#2c3e50` instead of default blue).

Terraform (when ready): `aws_cognito_user_pool_ui_customization` with `image_file` + `css`.

Placeholder SVG (text header) still at:

`~/Hive-Poc/portal26-hive-auth-poc/infra/cognito/assets/hive-login-header.svg`

## Preview

App client → **View login page**, or `http://localhost:8080/api/v1/auth/login` with the API running.
