package com.emart.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.emart.model.Product;
import com.emart.security.AuthenticatedUser;
import com.emart.service.ProductService;

/**
 * Reads of the public catalogue are open; writes and /mine require the RETAILER role (enforced in
 * SecurityConfig) and the owner is always taken from the verified principal.
 */
@RestController
@CrossOrigin
@RequestMapping("/api")
public class ProductController {
    @Autowired
    private ProductService productService;

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        if (products.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/products/mine")
    public List<Product> getMyProducts(@AuthenticationPrincipal AuthenticatedUser user) {
        return productService.getProductsOwnedBy(user.id());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return new ResponseEntity<>(productService.getProductById(id), HttpStatus.OK);
    }

    @GetMapping("/products/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable String id) {
        Product product = productService.getProductById(id);
        byte[] imageData = product.getImageData();
        if (imageData == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        String imageType = product.getImageType();
        MediaType type = (imageType == null || imageType.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.valueOf(imageType);
        return ResponseEntity.ok().contentType(type).body(imageData);
    }

    @PostMapping("/products")
    public ResponseEntity<Void> addProduct(@RequestPart Product product,
                                           @RequestPart(required = false) MultipartFile imageFile,
                                           @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        productService.addProduct(product, imageFile, user.id());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping("/products/update/{id}")
    public ResponseEntity<Void> updateProduct(@PathVariable String id,
                                              @RequestPart Product product,
                                              @RequestPart(required = false) MultipartFile imageFile,
                                              @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        productService.updateProduct(id, product, imageFile, user.id());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping("/products/delete/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        productService.deleteProduct(id, user.id());
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
