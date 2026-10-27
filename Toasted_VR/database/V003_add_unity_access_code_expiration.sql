alter table if exists public.unity_access_codes
  add column if not exists expires_at timestamp;

