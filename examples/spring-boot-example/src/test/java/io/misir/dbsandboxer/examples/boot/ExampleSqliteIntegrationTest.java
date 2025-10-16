package io.misir.dbsandboxer.examples.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.misir.dbsandboxer.examples.boot.domain.Product;
import io.misir.dbsandboxer.examples.boot.domain.PurchaseOrder;
import io.misir.dbsandboxer.examples.boot.repository.ProductRepository;
import io.misir.dbsandboxer.examples.boot.repository.PurchaseOrderRepository;
import io.misir.dbsandboxer.starter.EnableDbSandboxer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = Application.class)
@EnableDbSandboxer(templateDatabaseName = "example_sqlite_template")
@ActiveProfiles("sqlite")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExampleSqliteIntegrationTest {

    @Autowired private ProductRepository products;

    @Autowired private PurchaseOrderRepository orders;

    @Test
    void testFixturesAreLoaded() {
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
        var initialProducts = products.findAll();
        assertThat(initialProducts).hasSize(3);

        Product newProduct = new Product("NEW-SKU", "New Product", 999);
        products.save(newProduct);
        assertThat(products.findAll()).hasSize(4);
    }

    @Test
    void endToEndProductThenOrder() {
        var initialProducts = products.findAll();
        assertThat(initialProducts).hasSize(3);

        Product product = new Product("SKU-1", "Widget", 1299);
        product = products.save(product);
        var all = products.findAll();
        assertThat(all).hasSize(4);

        PurchaseOrder order = new PurchaseOrder(product, 2, 2598);
        orders.save(order);
        var ordersAll = orders.findAll();
        assertThat(ordersAll).hasSize(3);
    }
}
