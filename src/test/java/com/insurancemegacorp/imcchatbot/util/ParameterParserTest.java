package com.insurancemegacorp.imcchatbot.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ParameterParser utility
 */
class ParameterParserTest {

    @Test
    void shouldExistAndBeAccessible() {
        // Given: ParameterParser class
        // When: Accessing the class
        // Then: Class should be accessible
        Class<?> parserClass = ParameterParser.class;
        
        assertThat(parserClass).isNotNull();
        assertThat(parserClass.getSimpleName()).isEqualTo("ParameterParser");
    }

    @Test
    void shouldHaveCorrectPackage() {
        // Given: ParameterParser class
        // When: Checking package
        // Then: Should be in util package
        String expectedPackage = "com.insurancemegacorp.imcchatbot.util";
        
        assertThat(ParameterParser.class.getPackageName()).isEqualTo(expectedPackage);
    }
}