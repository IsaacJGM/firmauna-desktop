package unap.oti.firmauna.windows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledOnOs(OS.WINDOWS)
class WindowsCertificateProviderTest {

    @Test
    void opensWindowsMyWithSunMscapiWithoutUsingPrivateKeys() throws Exception {
        WindowsCertificateProvider provider = new WindowsCertificateProvider();
        try {
            provider.open(null);

            assertFalse(provider.requiresApplicationPin());
            assertNotNull(provider.getSignatureProvider());
            assertEquals("SunMSCAPI", provider.getSignatureProvider().getName());
            provider.getCertificateChoices().forEach(choice ->
                assertFalse(choice.certificateChain().isEmpty()));
        } finally {
            provider.close();
        }
    }
}
