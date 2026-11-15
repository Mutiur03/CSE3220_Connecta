CREATE INDEX user_roles_role_id_idx ON user_roles (role_id);

ALTER TABLE roles ADD CONSTRAINT roles_name_check CHECK (name IN ('OWNER', 'ADMIN', 'AGENT'));
ALTER TABLE users ADD CONSTRAINT users_email_not_blank CHECK (btrim(email) <> '');
ALTER TABLE users ADD CONSTRAINT users_password_hash_not_blank CHECK (btrim(password_hash) <> '');
