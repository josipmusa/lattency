package dev.lattency.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuiltInSinksTest {
    @Test
    void recognizesSpringAndJCacheCachingAnnotations() {
        assertTrue(BuiltInSinks.isCachingAnnotation(
                "org.springframework.cache.annotation.Cacheable"));
        assertTrue(BuiltInSinks.isCachingAnnotation("javax.cache.annotation.CacheResult"));
        assertTrue(BuiltInSinks.isCachingAnnotation("jakarta.cache.annotation.CacheResult"));
        assertFalse(BuiltInSinks.isCachingAnnotation(
                "org.springframework.cache.annotation.CacheEvict"));
    }
}
