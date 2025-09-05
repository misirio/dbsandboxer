package io.misir.dbsandboxer.examples.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.misir.dbsandboxer.examples.boot.domain.Product;
import io.misir.dbsandboxer.examples.boot.domain.PurchaseOrder;
import io.misir.dbsandboxer.examples.boot.repository.ProductRepository;
import io.misir.dbsandboxer.examples.boot.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Demo test class to showcase fast test execution with dbsandboxer. Contains 100 parameterized
 * tests that will show individually in IntelliJ.
 */
@Disabled // disabled - is for showcase only
class DemoSpeedTest extends BaseIntegrationTest {

    @Autowired private ProductRepository products;
    @Autowired private PurchaseOrderRepository orders;

    @ParameterizedTest(name = "Test execution demo #{0}")
    @ValueSource(
            ints = {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                41, 42, 43, 44, 45, 46, 47, 48, 49, 50,
                51, 52, 53, 54, 55, 56, 57, 58, 59, 60,
                61, 62, 63, 64, 65, 66, 67, 68, 69, 70,
                71, 72, 73, 74, 75, 76, 77, 78, 79, 80,
                81, 82, 83, 84, 85, 86, 87, 88, 89, 90,
                91, 92, 93, 94, 95, 96, 97, 98, 99, 100
            })
    void testDatabaseOperationsWithSandbox(int testNumber) {
        // Each test starts with clean fixtures - verify initial state
        var initialProducts = products.findAll();
        assertThat(initialProducts).hasSize(3);
        assertThat(initialProducts)
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("WIDGET-001", "GADGET-002", "TOOL-003");

        // Create a unique product for this test run
        Product newProduct =
                new Product(
                        String.format("DEMO-SKU-%03d", testNumber),
                        String.format("Demo Product %d", testNumber),
                        1000 + testNumber);
        newProduct = products.save(newProduct);
        assertThat(newProduct.getId()).isNotNull();

        // Verify product was saved
        var allProducts = products.findAll();
        assertThat(allProducts).hasSize(4); // 3 fixtures + 1 new

        // Create an order for the new product
        PurchaseOrder order =
                new PurchaseOrder(
                        newProduct,
                        testNumber % 10 + 1, // quantity varies 1-10
                        (1000 + testNumber) * (testNumber % 10 + 1));
        order = orders.save(order);
        assertThat(order.getId()).isNotNull();

        // Verify order was saved
        var allOrders = orders.findAll();
        assertThat(allOrders).hasSize(3); // 2 fixtures + 1 new

        // Verify we can find the specific product we created
        var specificProduct = products.findById(newProduct.getId());
        assertThat(specificProduct).isPresent();
        assertThat(specificProduct.get().getName())
                .isEqualTo(String.format("Demo Product %d", testNumber));

        // Each test is isolated - changes here won't affect other tests
        // This demonstrates dbsandboxer's fast sandbox reset between tests
    }
}
