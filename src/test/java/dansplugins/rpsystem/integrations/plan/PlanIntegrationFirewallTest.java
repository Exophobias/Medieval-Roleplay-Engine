package dansplugins.rpsystem.integrations.plan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlanIntegrationFirewallTest {

    @Test
    void dependencyFreeFacadeHasNoPlanClassDescriptor() throws IOException {
        String resource = "/" + PlanIntegration.class.getName().replace('.', '/') + ".class";
        try (InputStream input = PlanIntegration.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            String bytecode = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(bytecode.contains("com/djrapitops/plan"));
        }
    }
}
