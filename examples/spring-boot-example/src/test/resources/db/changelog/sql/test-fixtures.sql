-- Test fixtures for products
INSERT INTO product (sku, name, price_cents) VALUES
                                                 ('WIDGET-001', 'Premium Widget', 2499),
                                                 ('GADGET-002', 'Smart Gadget', 4999),
                                                 ('TOOL-003', 'Professional Tool', 15999);


-- Test fixtures for purchase orders
INSERT INTO purchase_order (product_id, quantity, total_cents)
SELECT id, 2, 4998 FROM product WHERE sku = 'WIDGET-001';

INSERT INTO purchase_order (product_id, quantity, total_cents)
SELECT id, 1, 4999 FROM product WHERE sku = 'GADGET-002';