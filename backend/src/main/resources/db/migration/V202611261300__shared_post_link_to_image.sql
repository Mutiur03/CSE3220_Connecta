-- #329: a regular Instagram post arrives as a signed CDN image (lookaside.fbsbx.com), not a page. #325 stored
-- it as LINK; make those rows IMAGE so they are shown and copied (if the signed url is still valid).
UPDATE message_attachments
SET kind = 'IMAGE',
    source_url = COALESCE(source_url, url)
WHERE kind = 'LINK'
  AND url ~* '^https?://lookaside\.fbsbx\.com/';
