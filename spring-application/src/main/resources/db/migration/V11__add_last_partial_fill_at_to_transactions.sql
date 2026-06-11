-- Stores the timestamp of the last partial fill event independently of updated_at.
-- updated_at is overwritten by cancel(), losing the fill time for PARTIAL→CANCELED orders.
-- This column is set only by partialFill() and remains stable through subsequent status changes.
ALTER TABLE transactions ADD COLUMN last_partial_fill_at TIMESTAMPTZ;
