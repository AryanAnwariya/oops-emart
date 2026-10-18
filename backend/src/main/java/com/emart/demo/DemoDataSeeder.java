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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Optional, repeatable demo data for presentations. Off by default.
 *
 * <p>Only exists when {@code app.demo.seed=true} (env {@code DEMO_SEED=true}). All demo accounts share one password
 * that must be supplied through {@code DEMO_PASSWORD} (at least 12 characters); no password is stored in the
 * repository, none is ever logged, and the application refuses to start in demo mode without one.
 *
 * <p>Safety rules:
 * <ul>
 *   <li>Seeding runs as a {@link SmartInitializingSingleton}, i.e. before the embedded web server starts accepting
 *       requests, so nobody can register a demo email first (account squatting) through the public API.</li>
 *   <li>It uses the normal services, so passwords are BCrypt-hashed, roles validated and products owned by the
 *       seller.</li>
 *   <li>It only ever <em>creates</em>. An existing demo account is never modified: if its role or password does not
 *       match, the seeder warns and attaches no products to it.</li>
 *   <li>Idempotent: each account and each product (per owner and name) is created only when missing.</li>
 * </ul>
 * With the in-memory H2 database every restart begins empty, so the seeder simply recreates the data on each boot;
 * anything created during a demo is lost on restart.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final int MIN_PASSWORD_LENGTH = 12;

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
    private final BCryptPasswordEncoder encoder;

    @Value("${app.demo.password:}")
    private String password;

    public DemoDataSeeder(AuthService authService, ProductService productService,
                          WholesalerProductService wholesalerProductService, UserRepo userRepo,
                          BCryptPasswordEncoder encoder) {
        this.authService = authService;
        this.productService = productService;
        this.wholesalerProductService = wholesalerProductService;
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    static List<String> retailProductNames() {
        return Stream.concat(RETAILER_1_ITEMS.stream(), RETAILER_2_ITEMS.stream()).map(Item::name).toList();
    }

    static List<String> wholesaleProductNames() {
        return WHOLESALER_ITEMS.stream().map(Item::name).toList();
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            seed();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Demo data seeding failed", e);
        }
    }

    /** Creates any missing demo accounts and products. Safe to call repeatedly. */
    public void seed() throws Exception {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "Demo seeding is enabled but DEMO_PASSWORD is missing or shorter than " + MIN_PASSWORD_LENGTH
                            + " characters. Choose a strong presentation-only password; it is never stored "
                            + "in the repository.");
        }
        log.warn("DEMO MODE IS ON: seeding well-known demo accounts. Use only for presentations and turn "
                + "DEMO_SEED off afterwards.");

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

        if (retailer1 != null) for (Item i : RETAILER_1_ITEMS) addRetailProduct(retailer1, i);
        if (retailer2 != null) for (Item i : RETAILER_2_ITEMS) addRetailProduct(retailer2, i);
        if (wholesaler != null) for (Item i : WHOLESALER_ITEMS) addWholesaleProduct(wholesaler, i);

        log.info("Demo data ready: 1 customer, 2 retailers, 1 wholesaler (password = the DEMO_PASSWORD you set).");
    }

    /**
     * Returns the demo user to attach products to, or null if the email is already taken by an account that is not
     * the expected demo account. An existing account is never changed.
     */
    private User createUser(String email, String role, Customer c, Retailer r, Wholesaler w) {
        Optional<User> existing = userRepo.findByEmail(email);
        if (existing.isPresent()) {
            User u = existing.get();
            boolean genuine = role.equals(u.getRole()) && u.getPassword() != null
                    && encoder.matches(password, u.getPassword());
            if (!genuine) {
                log.warn("Account {} already exists but is not the expected demo account (role or password differs). "
                        + "It was left untouched and no demo products were attached to it.", email);
                return null;
            }
            return u;
        }
        return authService.registerUser(new User(email, password, role, null), c, r, w);
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
