package com.emart.controller;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.emart.service.WholesalerProductService;
import com.emart.model.WholesalerProduct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@CrossOrigin
@RequestMapping("/api")
public class WholesalerProductController {
    @Autowired
    private WholesalerProductService productService;

    @GetMapping("/wholesaler/products")
    public ResponseEntity<List<WholesalerProduct>> getAllProducts() {
        try {
            List<WholesalerProduct> products = productService.getAllProducts();
            if (products.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            else {
                return new ResponseEntity<>(products, HttpStatus.OK);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/wholesaler/products/{id}")
    public ResponseEntity<WholesalerProduct> getProductById(@PathVariable String id) {
        try {
            WholesalerProduct product = productService.getProductById(id);
            if (product != null) {
                return new ResponseEntity<>(product, HttpStatus.OK);
            }
            else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/wholesaler/products/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable String id) {
        try {
            WholesalerProduct product = productService.getProductById(id);
            byte[] imageData = product.getImageData();
            if (imageData == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            else {
                return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(product.getImageType()))
                    .body(imageData);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/wholesaler/products")
    public ResponseEntity<Void> addProduct(@RequestPart WholesalerProduct product,
                                        @RequestPart(required = false) MultipartFile imageFile) {
        try {
            productService.addProduct(product, imageFile);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/wholesaler/products/update/{id}")
    public ResponseEntity<Void> updateProduct(@PathVariable String id,
                                             @RequestPart WholesalerProduct product,
                                             @RequestPart(required = false) MultipartFile imageFile) {
        try {
            productService.updateProduct(id, product, imageFile);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/wholesaler/products/delete/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        try {
            productService.deleteProduct(id);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

