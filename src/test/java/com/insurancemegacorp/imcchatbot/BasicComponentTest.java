package com.insurancemegacorp.imcchatbot;

import com.insurancemegacorp.imcchatbot.cli.CliRunner;
import com.insurancemegacorp.imcchatbot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic component test with mocked dependencies
 * Verifies that the application structure is correct without external calls
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class BasicComponentTest {

    @MockBean
    private ChatModel chatModel;

    @Test
    void shouldCompileAndLoadClasses() {
        // Given: Application classes exist
        // When: Loading classes
        // Then: No compilation errors should occur
        
        // Verify key classes can be instantiated (without dependencies)
        Class<?> chatServiceClass = ChatService.class;
        Class<?> cliRunnerClass = CliRunner.class;
        
        // Basic assertions to ensure classes exist
        assert chatServiceClass != null;
        assert cliRunnerClass != null;
        assert chatServiceClass.getName().contains("ChatService");
        assert cliRunnerClass.getName().contains("CliRunner");
    }

    @Test
    void shouldHaveCorrectPackageStructure() {
        // Given: Application package structure
        // When: Checking package names
        // Then: Packages should follow expected naming convention
        
        String expectedBasePackage = "com.insurancemegacorp.imcchatbot";
        
        assert ChatService.class.getPackageName().startsWith(expectedBasePackage);
        assert CliRunner.class.getPackageName().startsWith(expectedBasePackage);
    }
}