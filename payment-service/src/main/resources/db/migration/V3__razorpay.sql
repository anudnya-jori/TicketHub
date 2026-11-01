ALTER TABLE payments ADD COLUMN provider_order_id   VARCHAR(100);
ALTER TABLE payments ADD COLUMN provider_payment_id VARCHAR(100);
CREATE UNIQUE INDEX uq_payments_provider_order
    ON payments (provider_order_id) WHERE provider_order_id IS NOT NULL;