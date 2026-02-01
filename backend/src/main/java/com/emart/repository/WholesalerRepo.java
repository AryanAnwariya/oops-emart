package com.emart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emart.model.Wholesaler;
import com.emart.model.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WholesalerRepo extends JpaRepository<Wholesaler, Integer> {
    Optional<Wholesaler> findByUser(User user);
}

