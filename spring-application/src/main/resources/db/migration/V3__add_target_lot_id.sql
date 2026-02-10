ALTER TABLE transactions ADD COLUMN target_lot_id UUID REFERENCES transactions(id);
