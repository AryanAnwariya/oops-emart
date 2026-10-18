package com.emart.service;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.emart.exception.ResourceNotFoundException;
import com.emart.model.Product;
import com.emart.repository.ProductRepo;

/**
 * Retailer catalogue. Ownership always comes from the authenticated principal passed in by the controller;
 * nothing in the request body can set the id or the owner.
 */
@Service
public class ProductService {
    @Autowired
    private ProductRepo repo;

    public List<Product> getAllProducts() {
        return repo.findAll();
    }

    public List<Product> getProductsOwnedBy(Integer ownerId) {
        return repo.findByOwnerId(ownerId);
    }

    public Product getProductById(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Invalid id");
        }
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    public void addProduct(Product product, MultipartFile imageFile, Integer ownerId) throws IOException {
        validate(product);
        product.setId(null); // a client-chosen id would make save() overwrite someone else's row
        product.setOwnerId(ownerId);
        if (imageFile != null && !imageFile.isEmpty()) {
            ImageUpload.Validated image = ImageUpload.validate(imageFile);
            product.setImageName(image.name());
            product.setImageType(image.contentType());
            product.setImageData(image.data());
        }
        repo.save(product);
    }

    public void updateProduct(String id, Product product, MultipartFile imageFile, Integer callerId) throws IOException {
        validate(product);
        Product existing = getOwnedProduct(id, callerId);
        ImageUpload.Validated image = (imageFile != null && !imageFile.isEmpty()) ? ImageUpload.validate(imageFile) : null;
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setCategory(product.getCategory());
        existing.setPrice(product.getPrice());
        existing.setQuantity(product.getQuantity());
        // Update image only if a new one is provided
        if (image != null) {
            existing.setImageName(image.name());
            existing.setImageType(image.contentType());
            existing.setImageData(image.data());
        }
        repo.save(existing);
    }

    public void deleteProduct(String id, Integer callerId) {
        repo.delete(getOwnedProduct(id, callerId));
    }

    private Product getOwnedProduct(String id, Integer callerId) {
        Product existing = getProductById(id);
        if (callerId == null || !callerId.equals(existing.getOwnerId())) {
            throw new AccessDeniedException("Not the owner of this product");
        }
        return existing;
    }

    private static void validate(Product p) {
        if (p == null) {
            throw new IllegalArgumentException("Product is required");
        }
        if (p.getName() == null || p.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (p.getName().length() > 100) {
            throw new IllegalArgumentException("Product name must be at most 100 characters");
        }
        if (p.getDescription() != null && p.getDescription().length() > 255) {
            throw new IllegalArgumentException("Description must be at most 255 characters");
        }
        if (p.getCategory() != null && p.getCategory().length() > 100) {
            throw new IllegalArgumentException("Category must be at most 100 characters");
        }
        if (p.getPrice() < 0 || Double.isNaN(p.getPrice()) || Double.isInfinite(p.getPrice())) {
            throw new IllegalArgumentException("Price must be a non-negative number");
        }
        if (p.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity must not be negative");
        }
    }
}
