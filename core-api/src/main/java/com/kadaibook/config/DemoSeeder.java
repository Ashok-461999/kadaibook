package com.kadaibook.config;

import com.kadaibook.domain.Customer;
import com.kadaibook.domain.Role;
import com.kadaibook.domain.Shop;
import com.kadaibook.domain.User;
import com.kadaibook.repository.CustomerRepository;
import com.kadaibook.repository.ShopRepository;
import com.kadaibook.repository.UserRepository;
import com.kadaibook.service.CustomerMatcher;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final List<String> CUSTOMERS = List.of("Ravi Kumar", "Lakshmi", "Murugan");

    private final DemoProperties properties;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoSeeder(
            DemoProperties properties,
            UserRepository userRepository,
            ShopRepository shopRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User user = userRepository.findByPhone(properties.phone()).orElseGet(this::createUser);
        Shop shop = shopRepository.findAllByOwnerIdOrderByCreatedAtAsc(user.getId()).stream()
                .findFirst()
                .orElseGet(() -> createShop(user));
        for (String name : CUSTOMERS) {
            String normalized = CustomerMatcher.normalise(name);
            if (!customerRepository.existsByShopIdAndNormalizedName(shop.getId(), normalized)) {
                Customer customer = new Customer();
                customer.setShop(shop);
                customer.setDisplayName(name);
                customer.setNormalizedName(normalized);
                customer.setAliases(List.of());
                customerRepository.save(customer);
            }
        }
        log.info("demo shop ready: sign in as {} (shop {})", properties.phone(), shop.getId());
    }

    private User createUser() {
        User user = new User();
        user.setPhone(properties.phone());
        user.setDisplayName(properties.displayName());
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setRole(Role.SHOPKEEPER);
        return userRepository.save(user);
    }

    private Shop createShop(User user) {
        Shop shop = new Shop();
        shop.setName(properties.shopName());
        shop.setOwner(user);
        shop.setConsentAt(Instant.now());
        return shopRepository.save(shop);
    }
}
