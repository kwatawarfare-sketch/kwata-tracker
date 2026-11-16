# Security

Kwata Tracker is built by Kwata Enterprise, under Motswana Intelligence.

## Keys in this repository
- **No secret keys are stored in this code.** The only key in the app is the Supabase *publishable* key, which is designed to be public. It cannot read or change data on its own.
- All data access is enforced server-side by Supabase **row-level security (RLS)** on every table (`profiles`, `circle_members`, `locations`, `buzzes`, `sos_events`, `circle_invites`). Users only see their own data and the circles they have consented to join.
- Maps (CARTO, Esri) and weather (Open-Meteo, RainViewer) are keyless public services, so no API keys are shipped.
- Database passwords, service-role keys and deploy tokens are kept outside the repository and must never be committed.
- An optional Google Maps key entered in Settings is stored only on the user's own device.

## Reporting a vulnerability
Please do not open a public issue. Email **kwatawarfare@gmail.com** with details and we will respond promptly.
