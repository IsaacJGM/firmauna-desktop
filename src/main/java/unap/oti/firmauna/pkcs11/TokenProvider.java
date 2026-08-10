package unap.oti.firmauna.pkcs11;

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
import java.util.Enumeration;

/**
 * Loads and authenticates against Bit4id tokenME FIPS v3 via PKCS#11.
 */
public class TokenProvider {

    private static final String LIB_PATH = "/Library/bit4id/pkcs11/libbit4xpki.dylib";
    private Provider configuredProvider;
    private KeyStore sessionKeyStore;
    private String keyAlias;
    private char[] pin;

    /**
     * Initialize the PKCS#11 provider and login to the token.
     * Opens ONE session that all subsequent getters reuse.
     *
     * PIN is validated IMMEDIATELY by accessing the private key —
     * SunPKCS11.load() alone does not validate the PIN, so without
     * this check any PIN would appear to "work" but signing would fail.
     */
    public void login(String pin) throws Exception {
        Path cfg = createConfigFile();
        try {
            // Remove stale instance
            Provider stale = Security.getProvider("SunPKCS11-Bit4id");
            if (stale != null) Security.removeProvider("SunPKCS11-Bit4id");

            sun.security.pkcs11.SunPKCS11 base = new sun.security.pkcs11.SunPKCS11();
            Provider configured = base.configure(cfg.toString());
            Security.addProvider(configured);
            this.configuredProvider = configured;

            // Load once — this opens the single token session.
            this.sessionKeyStore = KeyStore.getInstance("PKCS11", configured);
            this.sessionKeyStore.load(null, pin.toCharArray());
            this.pin = pin.toCharArray();

            // Resolve the key alias and VALIDATE PIN immediately.
            // SunPKCS11.load() does NOT validate the PIN — only getKey()
            // with the actual PIN forces the token to check it.
            Enumeration<String> aliases = sessionKeyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (sessionKeyStore.isKeyEntry(alias)) {
                    this.keyAlias = alias;
                    // THROWS if PIN is wrong — UnrecoverableKeyException
                    this.sessionKeyStore.getKey(alias, pin.toCharArray());
                    break;
                }
            }
            if (this.keyAlias == null) {
                throw new Exception("No key entry found on token");
            }
        } finally {
            try { Files.deleteIfExists(cfg); } catch (IOException ignored) {}
        }
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
            throw new Exception("Token not logged in or key alias not resolved.");
        }
        return keyAlias;
    }

    public X509Certificate getCertificate() throws Exception {
        return (X509Certificate) getKeyStore().getCertificate(keyAlias);
    }

    public java.security.cert.Certificate[] getCertificateChain() throws Exception {
        return getKeyStore().getCertificateChain(keyAlias);
    }

    public PrivateKey getPrivateKey() throws Exception {
        return (PrivateKey) getKeyStore().getKey(keyAlias, pin);
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