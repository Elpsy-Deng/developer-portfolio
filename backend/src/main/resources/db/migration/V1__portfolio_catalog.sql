CREATE TABLE category (
    id uuid PRIMARY KEY,
    label varchar(40) NOT NULL UNIQUE CHECK (length(trim(label)) > 0),
    sort_order integer NOT NULL UNIQUE CHECK (sort_order >= 0)
);

CREATE TABLE project (
    id uuid PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    category_id uuid NOT NULL REFERENCES category(id),
    title text NOT NULL CHECK (length(trim(title)) > 0),
    subtitle text NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('Planned','In Progress','Completed')),
    status_note text NOT NULL,
    summary text NOT NULL,
    description text NOT NULL,
    year text NOT NULL,
    role text NOT NULL,
    duration text NOT NULL,
    featured boolean NOT NULL,
    published boolean NOT NULL DEFAULT true,
    theme varchar(12) NOT NULL CHECK (theme IN ('forest','blue','peach','violet')),
    technology_label text NOT NULL,
    sort_order integer NOT NULL UNIQUE CHECK (sort_order >= 0),
    version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    features jsonb NOT NULL CHECK (jsonb_typeof(features) = 'array'),
    planned_goals jsonb CHECK (planned_goals IS NULL OR jsonb_typeof(planned_goals) = 'array'),
    verification jsonb CHECK (verification IS NULL OR jsonb_typeof(verification) = 'array'),
    limitations jsonb CHECK (limitations IS NULL OR jsonb_typeof(limitations) = 'array'),
    challenges jsonb NOT NULL CHECK (jsonb_typeof(challenges) = 'array'),
    architecture jsonb NOT NULL CHECK (jsonb_typeof(architecture) = 'array'),
    architecture_description text,
    github_url text CHECK (github_url IS NULL OR github_url ~ '^https://'),
    live_demo_url text CHECK (live_demo_url IS NULL OR live_demo_url ~ '^https://'),
    local_demo_url text,
    evidence_checked_at date,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE technology (
    id uuid PRIMARY KEY,
    name varchar(80) NOT NULL UNIQUE CHECK (length(trim(name)) > 0)
);
CREATE TABLE project_technology (
    project_id uuid NOT NULL REFERENCES project(id),
    technology_id uuid NOT NULL REFERENCES technology(id),
    position integer NOT NULL CHECK (position >= 0),
    PRIMARY KEY (project_id, technology_id),
    UNIQUE (project_id, position)
);
CREATE TABLE project_media (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES project(id),
    kind varchar(12) NOT NULL CHECK (kind IN ('COVER','SCREENSHOT')),
    position integer NOT NULL CHECK (position >= 0),
    title text,
    description text,
    src text CHECK (src IS NULL OR src ~ '^/[^/]'),
    alt text,
    UNIQUE (project_id, kind, position),
    CHECK ((kind = 'COVER' AND position = 0 AND src IS NOT NULL AND alt IS NOT NULL)
        OR (kind = 'SCREENSHOT' AND title IS NOT NULL AND description IS NOT NULL))
);
CREATE TABLE project_evidence (
    project_id uuid NOT NULL REFERENCES project(id),
    position integer NOT NULL CHECK (position >= 0),
    label text NOT NULL CHECK (length(trim(label)) > 0),
    url text NOT NULL CHECK (url ~ '^https://'),
    PRIMARY KEY (project_id, position)
);

CREATE INDEX project_public_order ON project(sort_order) WHERE published;
CREATE INDEX project_category ON project(category_id) WHERE published;
