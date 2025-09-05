package io.misir.dbsandboxer.examples.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.misir.dbsandboxer.examples.boot.domain.Product;
import io.misir.dbsandboxer.examples.boot.domain.PurchaseOrder;
import io.misir.dbsandboxer.examples.boot.repository.ProductRepository;
import io.misir.dbsandboxer.examples.boot.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExampleIntegration1Test extends BaseIntegrationTest {

    @Autowired private ProductRepository products;
    @Autowired private PurchaseOrderRepository orders;

    @Test
    void testFixturesAreLoaded() {
        // Verify test fixtures are pre-loaded
        var allProducts = products.findAll();
        assertThat(allProducts).hasSize(3);
        assertThat(allProducts)
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("WIDGET-001", "GADGET-002", "TOOL-003");

        var allOrders = orders.findAll();
        assertThat(allOrders).hasSize(2);
    }

    @Test
    void testFixturesAreResetBetweenTests() {
        // Verify we start with clean fixtures
        var initialProducts = products.findAll();
        assertThat(initialProducts).hasSize(3);

        // Add a new product
        Product newProduct = new Product("NEW-SKU", "New Product", 999);
        products.save(newProduct);
        assertThat(products.findAll()).hasSize(4);

        // This product will be gone in the next test due to sandbox reset
    }

    @Test
    void endToEndProductThenOrder() {
        // Verify fixtures are reset - should have exactly 3 products again
        var initialProducts = products.findAll();
        assertThat(initialProducts).hasSize(3);

        Product product = new Product("SKU-1", "Widget", 1299);
        product = products.save(product);
        var all = products.findAll();
        assertThat(all).hasSize(4); // 3 fixtures + 1 new

        PurchaseOrder order = new PurchaseOrder(product, 2, 2598);
        orders.save(order);
        var ordersAll = orders.findAll();
        assertThat(ordersAll).hasSize(3); // 2 fixtures + 1 new
    }
}
