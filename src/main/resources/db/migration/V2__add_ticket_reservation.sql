ALTER TABLE tickets
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN reserved_until TIMESTAMPTZ;

ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_status_consistency CHECK (
        (status = 'AVAILABLE' AND user_id IS NULL AND reserved_until IS NULL) OR
        (status = 'RESERVED' AND user_id IS NOT NULL AND reserved_until IS NOT NULL) OR
        (status = 'SOLD' AND user_id IS NOT NULL AND reserved_until IS NULL)
    );

CREATE INDEX idx_tickets_expired_reservations
    ON tickets(reserved_until)
    WHERE status = 'RESERVED';
