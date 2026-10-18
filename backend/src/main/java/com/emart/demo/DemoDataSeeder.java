package com.emart.demo;

import com.emart.model.Customer;
import com.emart.model.Product;
import com.emart.model.Retailer;
import com.emart.model.User;
import com.emart.model.Wholesaler;
import com.emart.model.WholesalerProduct;
import com.emart.repository.UserRepo;
import com.emart.service.AuthService;
import com.emart.service.ProductService;
import com.emart.service.WholesalerProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Optional, repeatable demo data for LOCAL DEMOS ONLY.
 *
 * <p>Only exists when {@code app.demo.seed=true} (env {@code DEMO_SEED=true}); it is off by default, so a normal
 * or production start never creates demo accounts. All accounts share one password that must be supplied through
 * {@code DEMO_PASSWORD}; no password is stored in the repository and the application refuses to seed without one.
 * Data goes through the normal services (validation, BCrypt hashing, ownership), and re-running is idempotent
 * because the database is in-memory and each user/product is only created when missing.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String CUSTOMER = "demo.customer@demo.emart.test";
    public static final String RETAILER_1 = "demo.retailer1@demo.emart.test";
    public static final String RETAILER_2 = "demo.retailer2@demo.emart.test";
    public static final String WHOLESALER = "demo.wholesaler@demo.emart.test";

    private record Item(String name, String description, String category, double price, int quantity) {}

    private static final List<Item> RETAILER_1_ITEMS = List.of(
            new Item("Alphonso Mango (1 dozen)", "Sweet seasonal mangoes", "Fruit", 120, 40),
            new Item("Amul Butter 500g", "Salted table butter", "Dairy", 285, 25),
            new Item("Basmati Rice 1kg", "Long-grain aged basmati", "Grocery", 95, 8),
            new Item("Toor Dal 1kg", "Split pigeon peas", "Grocery", 140, 3));
    private static final List<Item> RETAILER_2_ITEMS = List.of(
            new Item("Green Tea (100 bags)", "Unflavoured green tea", "Beverages", 210, 15),
            new Item("A4 Notebook", "200-page ruled notebook", "Stationery", 60, 5));
    private static final List<Item> WHOLESALER_ITEMS = List.of(
            new Item("Basmati Rice 25kg Sack", "Bulk basmati for retailers", "Grocery", 1800, 200),
            new Item("Cooking Oil 15L Tin", "Refined sunflower oil", "Grocery", 1950, 40),
            new Item("Sugar 50kg Sack", "Refined sugar", "Grocery", 2100, 6));

    private final AuthService authService;
    private final ProductService productService;
    private final WholesalerProductService wholesalerProductService;
    private final UserRepo userRepo;

    @Value("${app.demo.password:}")
    private String password;

    public DemoDataSeeder(AuthService authService, ProductService productService,
                          WholesalerProductService wholesalerProductService, UserRepo userRepo) {
        this.authService = authService;
        this.productService = productService;
        this.wholesalerProductService = wholesalerProductService;
        this.userRepo = userRepo;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (password == null || password.length() < 8) {
            throw new IllegalStateException(
                    "Demo seeding is enabled but DEMO_PASSWORD is missing or shorter than 8 characters. "
                            + "Choose any local-only password; it is never stored in the repository.");
        }

        Customer customer = new Customer();
        customer.setName("Demo Customer");
        customer.setPhone("0000000000");
        customer.setAddress("1 Demo Street, Localhost");
        createUser(CUSTOMER, "CUSTOMER", customer, null, null);

        Retailer r1 = new Retailer();
        r1.setName("Demo Retailer One");
        r1.setShopName("Fresh Corner");
        r1.setLocation("Pune");
        User retailer1 = createUser(RETAILER_1, "RETAILER", null, r1, null);

        Retailer r2 = new Retailer();
        r2.setName("Demo Retailer Two");
        r2.setShopName("Daily Needs");
        r2.setLocation("Mumbai");
        User retailer2 = createUser(RETAILER_2, "RETAILER", null, r2, null);

        Wholesaler w = new Wholesaler();
        w.setName("Demo Wholesaler");
        w.setBusinessId("DEMO-BIZ-001");
        w.setContact("0000000001");
        User wholesaler = createUser(WHOLESALER, "WHOLESALER", null, null, w);

        for (Item i : RETAILER_1_ITEMS) addRetailProduct(retailer1, i);
        for (Item i : RETAILER_2_ITEMS) addRetailProduct(retailer2, i);
        for (Item i : WHOLESALER_ITEMS) addWholesaleProduct(wholesaler, i);

        System.out.println("Demo data ready (local demo only): 1 customer, 2 retailers, 1 wholesaler. "
                + "Sign in with the password you set in DEMO_PASSWORD.");
    }

    private User createUser(String email, String role, Customer c, Retailer r, Wholesaler w) {
        return userRepo.findByEmail(email).orElseGet(() -> {
            User u = new User(email, password, role, null);
            return authService.registerUser(u, c, r, w);
        });
    }

    private void addRetailProduct(User owner, Item i) throws Exception {
        boolean exists = productService.getProductsOwnedBy(owner.getId()).stream()
                .anyMatch(p -> p.getName().equals(i.name()));
        if (exists) return;
        Product p = new Product(null, i.description(), i.category(), i.name(), i.price(), i.quantity());
        productService.addProduct(p, null, owner.getId());
    }

    private void addWholesaleProduct(User owner, Item i) throws Exception {
        boolean exists = wholesalerProductService.getProductsOwnedBy(owner.getId()).stream()
                .anyMatch(p -> p.getName().equals(i.name()));
        if (exists) return;
        WholesalerProduct p = new WholesalerProduct(null, i.description(), i.category(), i.name(), i.price(), i.quantity());
        wholesalerProductService.addProduct(p, null, owner.getId());
    }
}
