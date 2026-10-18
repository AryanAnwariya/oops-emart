package com.emart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emart.model.Product;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepo extends JpaRepository<Product, String> {
    java.util.List<Product> findByOwnerId(Integer ownerId);
}
