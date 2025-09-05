package io.misir.dbsandboxer.examples.boot.repository;

import io.misir.dbsandboxer.examples.boot.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {}
