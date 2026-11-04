CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE departments (
    id   bigserial PRIMARY KEY,
    name text      NOT NULL UNIQUE
);

CREATE TABLE users (
    id            bigserial PRIMARY KEY,
    email         citext    NOT NULL UNIQUE,
    password_hash text      NOT NULL,
    role          text      NOT NULL DEFAULT 'employee'
                  CHECK (role IN ('employee', 'manager', 'admin')),
    department_id bigint    REFERENCES departments (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX users_department_idx ON users (department_id);

CREATE TABLE resources (
    id            bigserial PRIMARY KEY,
    code          text      NOT NULL UNIQUE,
    calendar_code text      UNIQUE,
    name          text      NOT NULL,
    type          text      NOT NULL CHECK (type IN ('room', 'equipment', 'vehicle')),
    capacity      integer   NOT NULL CHECK (capacity >= 0),
    location      text      NOT NULL,
    features      text[]    NOT NULL DEFAULT '{}',
    timezone      text      NOT NULL DEFAULT 'Europe/Samara',
    status        text      NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX resources_features_idx ON resources USING gin (features);
CREATE INDEX resources_type_status_name_idx ON resources (type, status, name);
CREATE INDEX resources_location_idx ON resources (location);

CREATE TABLE resource_working_hours (
    resource_id bigint   NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    weekday     smallint NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_time  time     NOT NULL,
    end_time    time     NOT NULL,
    PRIMARY KEY (resource_id, weekday, start_time),
    CHECK (start_time < end_time)
);

CREATE TABLE booking_series (
    id          bigserial PRIMARY KEY,
    resource_id bigint    NOT NULL REFERENCES resources (id),
    user_id     bigint    NOT NULL REFERENCES users (id),
    weeks       integer   NOT NULL CHECK (weeks BETWEEN 1 AND 8),
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bookings (
    id                bigserial PRIMARY KEY,
    resource_id       bigint    NOT NULL REFERENCES resources (id),
    user_id           bigint    NOT NULL REFERENCES users (id),
    created_by_id     bigint    NOT NULL REFERENCES users (id),
    series_id         bigint    REFERENCES booking_series (id),
    starts_at         timestamptz NOT NULL,
    ends_at           timestamptz NOT NULL,
    status            text      NOT NULL DEFAULT 'confirmed'
                      CHECK (status IN ('confirmed', 'cancelled', 'moved', 'completed')),
    purpose           text      NOT NULL,
    idempotency_key   text,
    external_event_id text,
    sync_status       text      NOT NULL DEFAULT 'pending'
                      CHECK (sync_status IN ('pending', 'synced', 'failed')),
    cancelled_at      timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),
    CHECK (status <> 'cancelled' OR cancelled_at IS NOT NULL)
);

ALTER TABLE bookings
    ADD CONSTRAINT bookings_no_overlap
    EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('confirmed', 'moved'));

CREATE UNIQUE INDEX bookings_user_idempotency_uidx
    ON bookings (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX bookings_resource_starts_idx ON bookings (resource_id, starts_at);
CREATE INDEX bookings_user_status_starts_idx ON bookings (user_id, status, starts_at);
CREATE INDEX bookings_series_idx ON bookings (series_id);

CREATE TABLE reminders (
    id         bigserial PRIMARY KEY,
    booking_id bigint    NOT NULL UNIQUE REFERENCES bookings (id) ON DELETE CASCADE,
    remind_at  timestamptz NOT NULL,
    channel    text      NOT NULL DEFAULT 'log',
    status     text      NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sent', 'failed')),
    attempts   integer   NOT NULL DEFAULT 0,
    sent_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX reminders_status_remind_idx ON reminders (status, remind_at);

CREATE TABLE external_busy (
    id          bigserial PRIMARY KEY,
    resource_id bigint    NOT NULL REFERENCES resources (id),
    external_id text      NOT NULL,
    starts_at   timestamptz NOT NULL,
    ends_at     timestamptz NOT NULL,
    fetched_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (resource_id, external_id),
    CHECK (ends_at > starts_at)
);
CREATE INDEX external_busy_resource_starts_idx ON external_busy (resource_id, starts_at, ends_at);

CREATE TABLE sync_outbox (
    id              bigserial PRIMARY KEY,
    booking_id      bigint    NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    operation       text      NOT NULL CHECK (operation IN ('create', 'delete')),
    payload         jsonb     NOT NULL,
    attempts        integer   NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    status          text      NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'in_flight', 'sent', 'dead_letter')),
    last_error      text,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sync_outbox_status_next_attempt_idx ON sync_outbox (status, next_attempt_at);
CREATE UNIQUE INDEX sync_outbox_booking_operation_uidx
    ON sync_outbox (booking_id, operation)
    WHERE status <> 'sent';

CREATE TABLE audit_log (
    id           bigserial PRIMARY KEY,
    actor_id     bigint    REFERENCES users (id),
    action       text      NOT NULL,
    object_type  text      NOT NULL,
    object_id    text      NOT NULL,
    before_state jsonb,
    after_state  jsonb,
    request_id   text      NOT NULL,
    ip           inet,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_created_idx ON audit_log (created_at);
CREATE INDEX audit_log_object_idx ON audit_log (object_type, object_id);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_id);

CREATE TABLE login_attempts (
    id         bigserial PRIMARY KEY,
    email      citext    NOT NULL,
    ip         inet      NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX login_attempts_email_ip_created_idx ON login_attempts (email, ip, created_at);
