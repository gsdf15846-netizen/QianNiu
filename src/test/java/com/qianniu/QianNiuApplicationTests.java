package com.qianniu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {"qwen.api.key=test-key"})
class QianNiuApplicationTests {

    @Test
    void contextLoads() {
    }
}
