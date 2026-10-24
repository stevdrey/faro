package io.github.stevdrey.faro;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stevdrey.faro.speech.config.PrivacyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FaroApplicationTests {
    @Autowired
    private PrivacyProperties privacy;

    @Test
    void contextLoads() {
        assertThat(privacy.policy()).isEqualTo("LOCAL_ONLY");
    }
}
