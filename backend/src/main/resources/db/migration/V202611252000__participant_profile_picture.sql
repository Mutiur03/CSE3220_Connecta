-- The customer's latest Meta profile picture. The URL is signed and expires, so it is looked up again
-- once profile_picture_fetched_at is older than app.meta.profile-lookup.picture-ttl.
ALTER TABLE participants
    ADD COLUMN profile_picture_url        text,
    ADD COLUMN profile_picture_fetched_at timestamptz,
    ADD CONSTRAINT participants_profile_picture_url_https
        CHECK (profile_picture_url IS NULL
               OR (profile_picture_url LIKE 'https://%' AND char_length(profile_picture_url) <= 2048));
