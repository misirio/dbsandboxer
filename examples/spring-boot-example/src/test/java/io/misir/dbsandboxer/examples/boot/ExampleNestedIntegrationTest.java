package io.misir.dbsandboxer.examples.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.misir.dbsandboxer.examples.boot.domain.Product;
import io.misir.dbsandboxer.examples.boot.repository.ProductRepository;
import io.misir.dbsandboxer.examples.boot.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExampleNestedIntegrationTest extends BaseIntegrationTest {

    @Autowired private ProductRepository products;
    @Autowired private PurchaseOrderRepository orders;

    @Test
    void topLevelTestsStillStartWithFixtures() {
        assertThat(products.findAll()).hasSize(3);
        assertThat(orders.findAll()).hasSize(2);
    }

    @Nested
    class NestedSandboxScenarios {

        @Test
        void nestedTestsSeePreloadedFixtures() {
            assertThat(products.findAll()).hasSize(3);
        }

        @Test
        void nestedTestsRemainIsolatedBetweenRuns() {
            assertThat(products.findAll()).hasSize(3);

            Product added = new Product("NESTED-SKU", "Nested Product", 500);
            products.save(added);

            assertThat(products.findAll()).hasSize(4);
        }
    }

    @Test
    void sandboxResetsAfterNestedTests() {
        assertThat(products.findAll()).hasSize(3);
        assertThat(orders.findAll()).hasSize(2);
    }
}
