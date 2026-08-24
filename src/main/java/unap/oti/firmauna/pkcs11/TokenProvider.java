package unap.oti.firmauna.pkcs11;

import unap.oti.firmauna.certificate.CertificateChoice;
import unap.oti.firmauna.certificate.SigningProvider;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import java.util.concurrent.Semaphore;

/**
 * Loads and authenticates against Bit4id tokenME FIPS v3 via PKCS#11.
 */
public class TokenProvider implements SigningProvider {

    private static final String LIB_PATH = "/Library/bit4id/pkcs11/libbit4xpki.dylib";
    private static final Semaphore SESSION_PERMIT = new Semaphore(1);
    private Provider configuredProvider;
    private KeyStore sessionKeyStore;
    private String keyAlias;
    private char[] pin;
    private boolean sessionPermitHeld;
    private List<CertificateChoice> certificateChoices = List.of();

    /**
     * Initialize the PKCS#11 provider and login to the token.
     * Opens ONE session that all subsequent getters reuse.
     *
     * PIN is validated IMMEDIATELY by accessing the private key —
     * SunPKCS11.load() alone does not validate the PIN, so without
     * this check any PIN would appear to "work" but signing would fail.
     */
    @Override
    public void open(char[] pin) throws Exception {
        SESSION_PERMIT.acquire();
        sessionPermitHeld = true;
        Path cfg = null;
        try {
            cfg = createConfigFile();
            // Remove stale instance
            Provider stale = Security.getProvider("SunPKCS11-Bit4id");
            if (stale != null) Security.removeProvider("SunPKCS11-Bit4id");

            sun.security.pkcs11.SunPKCS11 base = new sun.security.pkcs11.SunPKCS11();
            Provider configured = base.configure(cfg.toString());
            Security.addProvider(configured);
            this.configuredProvider = configured;

            // Load once — this opens the single token session.
            this.sessionKeyStore = KeyStore.getInstance("PKCS11", configured);
            this.sessionKeyStore.load(null, pin);
            this.pin = pin.clone();

            // Enumerate every signing key alias and VALIDATE PIN immediately.
            // SunPKCS11.load() does NOT validate the PIN — only getKey()
            // with the actual PIN forces the token to check it.
            Enumeration<String> aliases = sessionKeyStore.aliases();
            List<CertificateChoice> choices = new ArrayList<>();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (sessionKeyStore.isKeyEntry(alias)) {
                    Certificate certificate = sessionKeyStore.getCertificate(alias);
                    Certificate[] chain = sessionKeyStore.getCertificateChain(alias);
                    if (certificate instanceof X509Certificate x509Certificate) {
                        choices.add(new CertificateChoice(alias, x509Certificate,
                            chain == null ? List.of() : Arrays.asList(chain)));
                    }
                }
            }
            if (choices.isEmpty()) {
                throw new Exception("No key entry found on token");
            }
            // THROWS if PIN is wrong — UnrecoverableKeyException.
            // Access one private key while keeping the same PKCS#11 session.
            this.sessionKeyStore.getKey(choices.getFirst().alias(), this.pin);
            this.certificateChoices = List.copyOf(choices);
        } catch (Exception exception) {
            logout();
            throw exception;
        } finally {
            if (cfg != null) {
                try { Files.deleteIfExists(cfg); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public boolean requiresApplicationPin() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "token";
    }

    /**
     * Returns the cached KeyStore session. Never calls load() again —
     * this is what prevents the "channel already in use" deadlock on
     * tokens that only allow a single PKCS#11 session.
     */
    public KeyStore getKeyStore() throws Exception {
        if (sessionKeyStore == null) {
            throw new Exception("Token not logged in. Call login() first.");
        }
        return sessionKeyStore;
    }

    public String getKeyAlias() throws Exception {
        if (keyAlias == null) {
            throw new Exception("No signing certificate has been selected.");
        }
        return keyAlias;
    }

    @Override
    public List<CertificateChoice> getCertificateChoices() throws Exception {
        getKeyStore();
        return certificateChoices;
    }

    /**
     * Selects one of the aliases discovered during this login session.
     */
    @Override
    public void selectKeyAlias(String alias) throws Exception {
        getKeyStore();
        if (certificateChoices.stream().noneMatch(choice -> choice.alias().equals(alias))) {
            throw new Exception("Unknown signing certificate alias.");
        }
        this.keyAlias = alias;
    }

    @Override
    public X509Certificate getCertificate() throws Exception {
        return (X509Certificate) getKeyStore().getCertificate(getKeyAlias());
    }

    @Override
    public java.security.cert.Certificate[] getCertificateChain() throws Exception {
        Certificate[] chain = getKeyStore().getCertificateChain(getKeyAlias());
        return chain == null || chain.length == 0
            ? new Certificate[]{getCertificate()}
            : chain.clone();
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        return (PrivateKey) getKeyStore().getKey(getKeyAlias(), pin);
    }

    public void logout() {
        try {
            if (sessionKeyStore != null) {
                // Explicitly close the PKCS#11 session to release the token
                sessionKeyStore.load(null, null);
            }
        } catch (Exception ignored) {}
        if (configuredProvider != null) {
            try {
                Security.removeProvider(configuredProvider.getName());
            } catch (Exception ignored) {}
        }
        this.sessionKeyStore = null;
        this.configuredProvider = null;
        this.keyAlias = null;
        this.certificateChoices = List.of();
        if (this.pin != null) {
            Arrays.fill(this.pin, '\0');
            this.pin = null;
        }
        if (sessionPermitHeld) {
            sessionPermitHeld = false;
            SESSION_PERMIT.release();
        }
    }

    @Override
    public void close() {
        logout();
    }

    private Path createConfigFile() throws IOException {
        Path temp = Files.createTempFile("pkcs11-Bit4id-", ".cfg");
        PrintWriter pw = new PrintWriter(new FileWriter(temp.toFile()));
        pw.println("name = Bit4id");
        pw.println("library = " + LIB_PATH);
        pw.close();
        return temp;
    }
}
