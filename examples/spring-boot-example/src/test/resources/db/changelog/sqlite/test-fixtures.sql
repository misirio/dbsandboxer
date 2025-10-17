-- Test fixtures for products
INSERT INTO product (id, sku, name, price_cents) VALUES
                                                 (1, 'WIDGET-001', 'Premium Widget', 2499),
                                                 (2, 'GADGET-002', 'Smart Gadget', 4999),
                                                 (3, 'TOOL-003', 'Professional Tool', 15999);

-- Test fixtures for purchase orders
INSERT INTO purchase_order (id, product_id, quantity, total_cents) VALUES
                                                 (1, 1, 2, 4998),
                                                 (2, 2, 1, 4999);
