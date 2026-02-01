package com.emart.service;
import com.emart.model.WholesalerProduct;
import com.emart.repository.WholesalerProductRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WholesalerProductService {

    @Autowired
    private WholesalerProductRepo repo;

    public List<WholesalerProduct> getAllProducts() throws Exception {
        List<WholesalerProduct> products = repo.findAll();
        // Return empty list instead of throwing exception
        return products;
    }
    
    public WholesalerProduct getProductById(String id) throws Exception {
        if (id == null) {
            throw new Exception("Invalid id");
        }
        WholesalerProduct product = repo.findById(id).orElse(null);
        if (product == null) {
            throw new Exception("Product not found");
        }
        return product;
    }
    
    public void addProduct(WholesalerProduct product, MultipartFile imageFile) throws IOException {
        if (imageFile != null) {
            product.setImageName(imageFile.getOriginalFilename());
            product.setImageType(imageFile.getContentType());
            product.setImageData(imageFile.getBytes());
        }
        repo.save(product);
    }
    
    public void updateProduct(String id, WholesalerProduct product, MultipartFile imageFile) throws IOException, Exception {
        if (id == null || product == null) {
            throw new Exception("Invalid id or product");
        }
        WholesalerProduct existingProduct = repo.findById(id).orElse(null);
        if (existingProduct == null) {
            throw new Exception("Product not found");
        }
        existingProduct.setName(product.getName());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setCategory(product.getCategory());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setQuantity(product.getQuantity());
        // Update image only if a new one is provided
        if (imageFile != null && !imageFile.isEmpty()) {
            existingProduct.setImageName(imageFile.getOriginalFilename());
            existingProduct.setImageType(imageFile.getContentType());
            existingProduct.setImageData(imageFile.getBytes());
        }
        repo.save(existingProduct);
    }   
    
    public void deleteProduct(String id) throws Exception {
        if (id == null) {
            throw new Exception("Invalid id");
        }
        if (!repo.existsById(id)) {
            throw new Exception("Product not found");
        }
        repo.deleteById(id);
    }
}

