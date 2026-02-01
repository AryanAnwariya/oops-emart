package com.emart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emart.model.WholesalerProduct;
import org.springframework.stereotype.Repository;

@Repository
public interface WholesalerProductRepo extends JpaRepository<WholesalerProduct, String> {
    
}

