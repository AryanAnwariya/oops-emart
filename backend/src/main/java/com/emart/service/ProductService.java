package com.emart.service;
import com.emart.model.Product;
import com.emart.repository.ProductRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

    @Autowired
    private ProductRepo repo;
    // private List<Product> products = new ArrayList<>();

    // public ProductService() {
    //     products.add(new Product(1, "Apple", 100, 10));
    //     products.add(new Product(2, "Banana", 200, 20));
    //     products.add(new Product(3, "Orange", 300, 30));
    // }

    public List<Product> getAllProducts() throws Exception {
        // Return list directly; an empty list is a valid response and should not cause a 500
        List<Product> products = repo.findAll();
        return products;
    }
    
    public Product getProductById(String id) throws Exception {
        if (id == null) {
            throw new Exception("Invalid id");
        }
        Product product = repo.findById(id).orElse(null);
        if (product == null) {
            throw new Exception("Product not found");
        }
        return product;
    }
    
    public void addProduct(Product product, MultipartFile imageFile) throws IOException {
        if (imageFile != null) {
            product.setImageName(imageFile.getOriginalFilename());
            product.setImageType(imageFile.getContentType());
            product.setImageData(imageFile.getBytes());
        }
        repo.save(product);
    }
    public void updateProduct(String id, Product product, MultipartFile imageFile) throws IOException, Exception {
        if (id == null || product == null) {
            throw new Exception("Invalid id or product");
        }
        Product existingProduct = repo.findById(id).orElse(null);
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