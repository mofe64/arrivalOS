create table app_users (
    id uuid primary key,
    full_name varchar(180) not null,
    email varchar(180) not null,
    phone varchar(40),
    password_hash varchar(255) not null,
    account_type varchar(40) not null,
    active boolean not null default true,
    email_verified boolean not null default false,
    email_verified_at timestamp with time zone,
    password_changed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_app_users_email unique (email)
);

create table account_invitations (
    id uuid primary key,
    full_name varchar(180) not null,
    email varchar(180) not null,
    phone varchar(40),
    account_type varchar(40) not null,
    token_hash varchar(255) not null,
    invited_by_user_id uuid not null,
    expires_at timestamp with time zone not null,
    accepted_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_account_invitations_invited_by
        foreign key (invited_by_user_id)
        references app_users (id)
        on delete cascade,
    constraint uq_account_invitations_token_hash
        unique (token_hash)
);

create table concierges (
    id uuid primary key,
    full_name varchar(160) not null,
    phone varchar(40) not null,
    photo_url varchar(500),
    public_id varchar(80) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_concierges_public_id unique (public_id)
);

create table trips (
    id uuid primary key,
    flight_number varchar(40) not null,
    arrival_airport varchar(120) not null,
    arrival_terminal varchar(80),
    meeting_point varchar(500),
    scheduled_arrival_at timestamp with time zone,
    actual_arrival_at timestamp with time zone,
    status varchar(60) not null,
    assigned_concierge_id uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_trips_assigned_concierge
        foreign key (assigned_concierge_id)
        references concierges (id)
);

create table trip_principals (
    id uuid primary key,
    trip_id uuid not null,
    user_account_id uuid,
    full_name varchar(180) not null,
    phone varchar(40),
    photo_url varchar(500),
    primary_contact boolean not null default false,
    sequence_number integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_trip_principals_trip
        foreign key (trip_id)
        references trips (id)
        on delete cascade,
    constraint fk_trip_principals_user_account
        foreign key (user_account_id)
        references app_users (id),
    constraint uq_trip_principals_trip_sequence
        unique (trip_id, sequence_number)
);

create table watchers (
    id uuid primary key,
    trip_id uuid not null,
    full_name varchar(180) not null,
    email varchar(180),
    phone varchar(40),
    notification_channel varchar(40) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_watchers_trip
        foreign key (trip_id)
        references trips (id)
        on delete cascade
);

create table concierge_trip_access (
    id uuid primary key,
    trip_id uuid not null,
    concierge_id uuid not null,
    token_hash varchar(255) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_concierge_trip_access_trip
        foreign key (trip_id)
        references trips (id)
        on delete cascade,
    constraint fk_concierge_trip_access_concierge
        foreign key (concierge_id)
        references concierges (id),
    constraint uq_concierge_trip_access_token_hash
        unique (token_hash)
);

create table trip_checkpoints (
    id uuid primary key,
    trip_id uuid not null,
    name varchar(120) not null,
    sequence_number integer not null,
    status varchar(40) not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    skipped_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_trip_checkpoints_trip
        foreign key (trip_id)
        references trips (id)
        on delete cascade,
    constraint uq_trip_checkpoints_trip_sequence
        unique (trip_id, sequence_number)
);

create table timeline_events (
    id uuid primary key,
    trip_id uuid not null,
    event_type varchar(80) not null,
    actor_type varchar(40) not null,
    actor_id uuid,
    checkpoint_name varchar(120),
    note varchar(1000),
    idempotency_key varchar(120),
    occurred_at timestamp with time zone not null,
    offline_created_at timestamp with time zone,
    metadata_json text,
    created_at timestamp with time zone not null,
    constraint fk_timeline_events_trip
        foreign key (trip_id)
        references trips (id)
        on delete cascade,
    constraint uq_timeline_events_trip_idempotency
        unique (trip_id, idempotency_key)
);

create table notification_attempts (
    id uuid primary key,
    trip_id uuid not null,
    recipient_type varchar(40) not null,
    recipient_id uuid,
    channel varchar(40) not null,
    provider varchar(80) not null,
    provider_message_id varchar(160),
    status varchar(40) not null,
    failure_reason varchar(1000),
    sent_at timestamp with time zone,
    delivered_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_notification_attempts_trip
        foreign key (trip_id)
        references trips (id)
        on delete cascade
);

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_id varchar(80) not null,
    token_hash varchar(255) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    last_used_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_refresh_tokens_user
        foreign key (user_id)
        references app_users (id)
        on delete cascade,
    constraint uq_refresh_tokens_token_id
        unique (token_id),
    constraint uq_refresh_tokens_token_hash
        unique (token_hash)
);

create table email_verification_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(255) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_email_verification_tokens_user
        foreign key (user_id)
        references app_users (id)
        on delete cascade,
    constraint uq_email_verification_tokens_token_hash
        unique (token_hash)
);

create table password_reset_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(255) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_password_reset_tokens_user
        foreign key (user_id)
        references app_users (id)
        on delete cascade,
    constraint uq_password_reset_tokens_token_hash
        unique (token_hash)
);

create table revoked_access_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_id varchar(80) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_revoked_access_tokens_user
        foreign key (user_id)
        references app_users (id)
        on delete cascade,
    constraint uq_revoked_access_tokens_token_id
        unique (token_id)
);

create index idx_trips_status on trips (status);
create index idx_trips_assigned_concierge on trips (assigned_concierge_id);
create index idx_account_invitations_email on account_invitations (email);
create index idx_account_invitations_token_hash on account_invitations (token_hash);
create index idx_trip_principals_trip on trip_principals (trip_id);
create index idx_trip_principals_user_account on trip_principals (user_account_id);
create index idx_watchers_trip on watchers (trip_id);
create index idx_concierge_trip_access_trip on concierge_trip_access (trip_id);
create index idx_concierge_trip_access_concierge on concierge_trip_access (concierge_id);
create index idx_trip_checkpoints_trip on trip_checkpoints (trip_id);
create index idx_timeline_events_trip_occurred on timeline_events (trip_id, occurred_at);
create index idx_notification_attempts_trip on notification_attempts (trip_id);
create index idx_refresh_tokens_user on refresh_tokens (user_id);
create index idx_refresh_tokens_token_id on refresh_tokens (token_id);
create index idx_email_verification_tokens_user on email_verification_tokens (user_id);
create index idx_email_verification_tokens_token_hash on email_verification_tokens (token_hash);
create index idx_password_reset_tokens_user on password_reset_tokens (user_id);
create index idx_password_reset_tokens_token_hash on password_reset_tokens (token_hash);
create index idx_revoked_access_tokens_user on revoked_access_tokens (user_id);
create index idx_revoked_access_tokens_token_id on revoked_access_tokens (token_id);
