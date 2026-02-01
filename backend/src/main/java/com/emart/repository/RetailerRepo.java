package com.emart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emart.model.Retailer;
import com.emart.model.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RetailerRepo extends JpaRepository<Retailer, Integer> {
    Optional<Retailer> findByUser(User user);
}

