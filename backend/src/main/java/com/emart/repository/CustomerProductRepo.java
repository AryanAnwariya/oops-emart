package com.emart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emart.model.CustomerProduct;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerProductRepo extends JpaRepository<CustomerProduct, String> {
    
}

