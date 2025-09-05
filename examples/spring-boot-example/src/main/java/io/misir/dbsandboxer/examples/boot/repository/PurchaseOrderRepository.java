package io.misir.dbsandboxer.examples.boot.repository;

import io.misir.dbsandboxer.examples.boot.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {}
