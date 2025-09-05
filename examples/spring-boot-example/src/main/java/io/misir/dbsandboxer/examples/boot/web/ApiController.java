package io.misir.dbsandboxer.examples.boot.web;

import io.misir.dbsandboxer.examples.boot.domain.Product;
import io.misir.dbsandboxer.examples.boot.domain.PurchaseOrder;
import io.misir.dbsandboxer.examples.boot.repository.ProductRepository;
import io.misir.dbsandboxer.examples.boot.repository.PurchaseOrderRepository;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final ProductRepository products;
    private final PurchaseOrderRepository orders;

    public ApiController(ProductRepository products, PurchaseOrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    @PostMapping("/products")
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> body) {
        Product product =
                new Product(
                        (String) body.get("sku"),
                        (String) body.get("name"),
                        ((Number) body.get("priceCents")).intValue());
        product = products.save(product);
        return Map.of("id", product.getId());
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> body) {
        Long productId = ((Number) body.get("productId")).longValue();
        Product product =
                products.findById(productId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Product not found: " + productId));

        PurchaseOrder order =
                new PurchaseOrder(
                        product,
                        ((Number) body.get("quantity")).intValue(),
                        ((Number) body.get("totalCents")).intValue());
        order = orders.save(order);
        return Map.of("id", order.getId());
    }

    @GetMapping("/products")
    public ResponseEntity<?> listProducts() {
        return ResponseEntity.ok(products.findAll());
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders() {
        return ResponseEntity.ok(orders.findAll());
    }
}
