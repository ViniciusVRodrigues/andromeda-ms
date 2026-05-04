CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    cloned_from_role_id UUID REFERENCES roles(id)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    module VARCHAR(100) NOT NULL
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Seed permissions
INSERT INTO permissions (code, description, module) VALUES
    ('USER_MANAGE',                  'Manage users in the organization',                'USERS'),
    ('ROLE_MANAGE',                  'Manage roles in the organization',                'ROLES'),
    ('AZURE_CONFIGURE',              'Configure Azure integration',                     'AZURE'),
    ('AZURE_READ',                   'Read Azure data',                                 'AZURE'),
    ('GITHUB_CONFIGURE',             'Configure GitHub integration',                    'GITHUB'),
    ('GITHUB_READ',                  'Read GitHub data',                                'GITHUB'),
    ('AI_READ_OWN',                  'Read own AI insights',                            'AI'),
    ('AI_READ_ALL',                  'Read all AI insights',                            'AI'),
    ('DASHBOARD_INDIVIDUAL',         'View individual dashboard',                       'DASHBOARD'),
    ('DASHBOARD_TEAM',               'View team dashboard',                             'DASHBOARD'),
    ('DASHBOARD_STRATEGIC',          'View strategic dashboard',                        'DASHBOARD'),
    ('PRICE_TABLE_MANAGE',           'Manage price tables',                             'PRICES'),
    ('CONNECTOR_TOKEN_MANAGE_OWN',   'Manage own connector tokens',                     'CONNECTOR'),
    ('CONNECTOR_TOKEN_MANAGE_ALL',   'Manage all connector tokens',                     'CONNECTOR');

-- Seed default roles (organization_id is NULL = global/default)
INSERT INTO roles (id, organization_id, name, is_default) VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'ADMIN',     TRUE),
    ('00000000-0000-0000-0000-000000000002', NULL, 'TECH_LEAD', TRUE),
    ('00000000-0000-0000-0000-000000000003', NULL, 'MANAGER',   TRUE),
    ('00000000-0000-0000-0000-000000000004', NULL, 'DEVELOPER', TRUE);

-- ADMIN: all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions;

-- TECH_LEAD permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id FROM permissions
WHERE code IN ('AZURE_READ','GITHUB_READ','AI_READ_ALL','DASHBOARD_TEAM','DASHBOARD_INDIVIDUAL','CONNECTOR_TOKEN_MANAGE_OWN');

-- MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id FROM permissions
WHERE code IN ('AZURE_READ','GITHUB_READ','AI_READ_ALL','DASHBOARD_STRATEGIC');

-- DEVELOPER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000004', id FROM permissions
WHERE code IN ('AI_READ_OWN','DASHBOARD_INDIVIDUAL','CONNECTOR_TOKEN_MANAGE_OWN');
