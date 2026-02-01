package com.emart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emart.model.Customer;
import com.emart.model.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepo extends JpaRepository<Customer, Integer> {
    Optional<Customer> findByUser(User user);
}

