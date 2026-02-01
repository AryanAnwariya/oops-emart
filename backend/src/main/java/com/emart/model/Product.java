package com.emart.model;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Lob;
import org.hibernate.annotations.GenericGenerator;

@Entity
public class Product {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    private String description;
    private String category;
    private String name;
    private double price;
    private int quantity;
    private String imageName;
    private String imageType;
    @Lob
    private byte[] imageData;

    public Product() {}

    public Product(String id, String description, String category, String name, double price, int quantity) {
        this.id = id;
        this.description = description;
        this.category = category;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    public String getImageName() {
        return imageName;
    }
    public String getImageType() {
        return imageType;
    }
    public byte[] getImageData() {
        return imageData;
    }
    public void setImageName(String imageName) {
        this.imageName = imageName;
    }
    public void setImageType(String imageType) {
        this.imageType = imageType;
    }
    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }
    public String getId() {
        return id;
    }
    public String getDescription() {
        return description;
    }
    public String getCategory() {
        return category;
    }
    public String getName() {
        return name;
    }
    public double getPrice() {
        return price;
    }
    public int getQuantity() {
        return quantity;
    }
    public void setId(String id) {
        this.id = id;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}