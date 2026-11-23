-- ============================================================================
-- BRAIN BUFFER — Supabase PostgreSQL Schema & Row-Level Security (RLS)
-- Migration: 001_brain_buffer_schema_and_rls.sql
--
-- Security design:
--   * Every user-owned table references auth.users(id) ON DELETE CASCADE.
--   * Row-Level Security is ENABLED on EVERY table in public schema.
--   * Policies strictly scope SELECT, INSERT, UPDATE, and DELETE to auth.uid().
--   * Raw per-minute phone usage events and notification text are NEVER stored.
--     Only opt-in aggregated daily_usage_summaries may be synced when explicitly
--     enabled by the user.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Shared updated_at trigger helper
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

-- ----------------------------------------------------------------------------
-- 1. User Profiles (1-to-1 with auth.users)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL DEFAULT '',
    email TEXT,
    avatar_key TEXT NOT NULL DEFAULT 'spark',
    study_level TEXT NOT NULL DEFAULT 'UNIVERSITY',
    course_or_exam_name TEXT NOT NULL DEFAULT '',
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Automatically provision a profile row on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id, email, display_name)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(
            NULLIF(NEW.raw_user_meta_data ->> 'display_name', ''),
            NULLIF(NEW.raw_user_meta_data ->> 'full_name', ''),
            NULLIF(NEW.raw_user_meta_data ->> 'name', ''),
            ''
        )
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "profiles_select_own" ON public.profiles;
CREATE POLICY "profiles_select_own"
    ON public.profiles FOR SELECT
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "profiles_insert_own" ON public.profiles;
CREATE POLICY "profiles_insert_own"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
CREATE POLICY "profiles_update_own"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "profiles_delete_own" ON public.profiles;
CREATE POLICY "profiles_delete_own"
    ON public.profiles FOR DELETE
    USING (auth.uid() = id);

-- ----------------------------------------------------------------------------
-- 2. Subjects
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    local_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    color_hex BIGINT NOT NULL DEFAULT 4286274810,
    icon_key TEXT NOT NULL DEFAULT 'book',
    target_hours_per_week REAL NOT NULL DEFAULT 4.0,
    exam_date_epoch_day BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, local_id)
);

CREATE INDEX IF NOT EXISTS idx_subjects_user_updated ON public.subjects (user_id, updated_at DESC);

CREATE TRIGGER trg_subjects_updated_at
    BEFORE UPDATE ON public.subjects
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.subjects ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "subjects_all_own" ON public.subjects;
CREATE POLICY "subjects_select_own" ON public.subjects FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "subjects_insert_own" ON public.subjects FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "subjects_update_own" ON public.subjects FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "subjects_delete_own" ON public.subjects FOR DELETE USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 3. Planner Tasks
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.planner_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    local_id BIGINT NOT NULL,
    subject_local_id BIGINT,
    title TEXT NOT NULL,
    due_date_epoch_day BIGINT NOT NULL,
    due_time_label TEXT NOT NULL DEFAULT '6:00 PM',
    estimated_minutes INTEGER NOT NULL DEFAULT 30,
    priority TEXT NOT NULL DEFAULT 'MEDIUM',
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at_ms BIGINT,
    notes TEXT NOT NULL DEFAULT '',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, local_id)
);

CREATE INDEX IF NOT EXISTS idx_planner_tasks_user_due ON public.planner_tasks (user_id, due_date_epoch_day);

CREATE TRIGGER trg_planner_tasks_updated_at
    BEFORE UPDATE ON public.planner_tasks
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.planner_tasks ENABLE ROW LEVEL SECURITY;

CREATE POLICY "planner_tasks_select_own" ON public.planner_tasks FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "planner_tasks_insert_own" ON public.planner_tasks FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "planner_tasks_update_own" ON public.planner_tasks FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "planner_tasks_delete_own" ON public.planner_tasks FOR DELETE USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 4. Focus Sessions
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.focus_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    session_key TEXT NOT NULL,
    subject_local_id BIGINT,
    subject_name TEXT NOT NULL DEFAULT 'General Study',
    subject_color_hex BIGINT NOT NULL DEFAULT 4286274810,
    duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
    planned_seconds INTEGER NOT NULL CHECK (planned_seconds >= 0),
    session_goal TEXT NOT NULL DEFAULT '',
    mode TEXT NOT NULL DEFAULT 'FOCUS',
    completed_at_ms BIGINT NOT NULL,
    date_epoch_day BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_key)
);

CREATE INDEX IF NOT EXISTS idx_focus_sessions_user_day ON public.focus_sessions (user_id, date_epoch_day DESC);

CREATE TRIGGER trg_focus_sessions_updated_at
    BEFORE UPDATE ON public.focus_sessions
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.focus_sessions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "focus_sessions_select_own" ON public.focus_sessions FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "focus_sessions_insert_own" ON public.focus_sessions FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "focus_sessions_update_own" ON public.focus_sessions FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "focus_sessions_delete_own" ON public.focus_sessions FOR DELETE USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 5. Focus Guard Settings & Mode Configurations
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.focus_settings (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    settings_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    notification_prefs_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    family_controls_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_focus_settings_updated_at
    BEFORE UPDATE ON public.focus_settings
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.focus_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "focus_settings_select_own" ON public.focus_settings FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "focus_settings_insert_own" ON public.focus_settings FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "focus_settings_update_own" ON public.focus_settings FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "focus_settings_delete_own" ON public.focus_settings FOR DELETE USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 6. Blocked Apps & Distraction Rules
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.blocked_apps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    app_label TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'OTHER',
    block_in_normal BOOLEAN NOT NULL DEFAULT FALSE,
    block_in_high BOOLEAN NOT NULL DEFAULT TRUE,
    block_in_custom BOOLEAN NOT NULL DEFAULT TRUE,
    is_blocked BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, package_name)
);

CREATE INDEX IF NOT EXISTS idx_blocked_apps_user ON public.blocked_apps (user_id);

CREATE TRIGGER trg_blocked_apps_updated_at
    BEFORE UPDATE ON public.blocked_apps
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.blocked_apps ENABLE ROW LEVEL SECURITY;

CREATE POLICY "blocked_apps_select_own" ON public.blocked_apps FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "blocked_apps_insert_own" ON public.blocked_apps FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "blocked_apps_update_own" ON public.blocked_apps FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "blocked_apps_delete_own" ON public.blocked_apps FOR DELETE USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 7. Study-Only YouTube Resources (Approved Videos, Playlists & Channels)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.study_resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    local_id BIGINT NOT NULL,
    subject_local_id BIGINT,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    resource_type TEXT NOT NULL DEFAULT 'VIDEO',
    channel_name TEXT NOT NULL DEFAULT '',
    last_opened_at_ms BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, local_id)
);

CREATE INDEX IF NOT EXISTS idx_study_resources_user ON public.study_resources (user_id, updated_at DESC);

CREATE TRIGGER trg_study_resources_updated_at
    BEFORE UPDATE ON public.study_resources
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.study_resources ENABLE ROW LEVEL SECURITY;

CREATE POLICY "study_resources_select_own" ON public.study_resources FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "study_resources_insert_own" ON public.study_resources FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "study_resources_update_own" ON public.study_resources FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "study_resources_delete_own" ON public.study_resources FOR DELETE USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 8. Opt-In Aggregated Daily Usage Summaries (NEVER raw usage logs or text)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.daily_usage_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    date_epoch_day BIGINT NOT NULL,
    total_screen_minutes INTEGER NOT NULL DEFAULT 0 CHECK (total_screen_minutes >= 0),
    study_minutes INTEGER NOT NULL DEFAULT 0 CHECK (study_minutes >= 0),
    distracting_minutes INTEGER NOT NULL DEFAULT 0 CHECK (distracting_minutes >= 0),
    category_totals_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, date_epoch_day)
);

CREATE INDEX IF NOT EXISTS idx_daily_usage_user_day ON public.daily_usage_summaries (user_id, date_epoch_day DESC);

CREATE TRIGGER trg_daily_usage_updated_at
    BEFORE UPDATE ON public.daily_usage_summaries
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.daily_usage_summaries ENABLE ROW LEVEL SECURITY;

CREATE POLICY "daily_usage_select_own" ON public.daily_usage_summaries FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "daily_usage_insert_own" ON public.daily_usage_summaries FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "daily_usage_update_own" ON public.daily_usage_summaries FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "daily_usage_delete_own" ON public.daily_usage_summaries FOR DELETE USING (auth.uid() = user_id);
