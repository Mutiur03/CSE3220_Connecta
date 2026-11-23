-- Where a session signed in from, looked up from the IP at issue time ("Dhaka, Bangladesh"). Null when unknown.
ALTER TABLE refresh_tokens
    ADD COLUMN location VARCHAR(100);
