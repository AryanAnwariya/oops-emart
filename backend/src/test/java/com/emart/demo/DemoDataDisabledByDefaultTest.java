package com.emart.demo;

import com.emart.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** Production safety: without app.demo.seed=true no demo bean (and no demo account) exists. */
@SpringBootTest
@ActiveProfiles("test")
class DemoDataDisabledByDefaultTest {

    @Autowired ApplicationContext context;
    @Autowired UserRepo userRepo;

    @Test
    void seederBean_isAbsentByDefault() {
        assertEquals(0, context.getBeansOfType(DemoDataSeeder.class).size());
    }
}
