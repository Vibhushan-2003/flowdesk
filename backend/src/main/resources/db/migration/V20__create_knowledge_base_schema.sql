-- V20__create_knowledge_base_schema.sql
-- Knowledge Base & Self-Service Support Schema

CREATE TABLE kb_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    icon VARCHAR(50) DEFAULT 'folder',
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_kb_categories_slug UNIQUE (slug),
    CONSTRAINT chk_kb_categories_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_kb_categories_slug_not_blank CHECK (btrim(slug) <> '')
);

CREATE INDEX idx_kb_categories_display_order ON kb_categories (display_order, name);
CREATE INDEX idx_kb_categories_active ON kb_categories (active);

CREATE TABLE kb_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES kb_categories(id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(250) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    content_markdown TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    view_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(summary, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(content_markdown, '')), 'C')
    ) STORED,

    CONSTRAINT uq_kb_articles_slug UNIQUE (slug),
    CONSTRAINT chk_kb_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_kb_articles_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT chk_kb_articles_slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT chk_kb_articles_summary_not_blank CHECK (btrim(summary) <> ''),
    CONSTRAINT chk_kb_articles_content_not_blank CHECK (btrim(content_markdown) <> ''),
    CONSTRAINT chk_kb_articles_view_count_non_negative CHECK (view_count >= 0)
);

CREATE INDEX idx_kb_articles_category ON kb_articles (category_id);
CREATE INDEX idx_kb_articles_status ON kb_articles (status);
CREATE INDEX idx_kb_articles_author ON kb_articles (author_user_id);
CREATE INDEX idx_kb_articles_published_at ON kb_articles (published_at DESC);
CREATE INDEX idx_kb_articles_view_count ON kb_articles (view_count DESC);
CREATE INDEX idx_kb_articles_search_vector ON kb_articles USING gin(search_vector);

-- Initial Categories Seed
INSERT INTO kb_categories (id, name, slug, description, icon, display_order, active)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Hardware & Peripherals', 'hardware-peripherals', 'Monitors, laptops, keyboards, docking stations, and audio devices.', 'laptop', 1, TRUE),
    ('a0000000-0000-0000-0000-000000000002', 'VPN & Network', 'vpn-network', 'Remote work VPN access, Wi-Fi connectivity, DNS, and proxy settings.', 'wifi', 2, TRUE),
    ('a0000000-0000-0000-0000-000000000003', 'Software & Tools', 'software-tools', 'Enterprise productivity apps, development tools, licenses, and installation guides.', 'tool', 3, TRUE),
    ('a0000000-0000-0000-0000-000000000004', 'Accounts & Access', 'accounts-access', 'Password management, single sign-on (SSO), 2FA, and permission requests.', 'shield', 4, TRUE),
    ('a0000000-0000-0000-0000-000000000005', 'Email & Collaboration', 'email-collaboration', 'Google Workspace, Outlook, calendar sharing, and Slack/Teams setup.', 'mail', 5, TRUE),
    ('a0000000-0000-0000-0000-000000000006', 'General IT & FAQs', 'general-it-faqs', 'Frequently asked questions, IT onboarding checklist, and company technology policies.', 'help', 6, TRUE);
