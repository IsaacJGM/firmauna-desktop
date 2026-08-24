package unap.oti.firmauna.windows;

import unap.oti.firmauna.certificate.CertificateChoice;
import unap.oti.firmauna.certificate.SigningProvider;

import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * Reads signing credentials from the current user's Windows Personal certificate store.
 */
public class WindowsCertificateProvider implements SigningProvider {

    private KeyStore keyStore;
    private Provider signatureProvider;
    private String keyAlias;
    private List<CertificateChoice> certificateChoices = List.of();

    @Override
    public void open(char[] authorization) throws Exception {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            throw new Exception("Windows-MY is only available on Windows.");
        }

        signatureProvider = Security.getProvider("SunMSCAPI");
        if (signatureProvider == null) {
            throw new Exception("The SunMSCAPI provider is not available.");
        }

        keyStore = KeyStore.getInstance("Windows-MY");
        keyStore.load(null, null);

        List<CertificateChoice> choices = new ArrayList<>();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }

            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate instanceof X509Certificate x509Certificate) {
                Certificate[] chain = keyStore.getCertificateChain(alias);
                List<Certificate> certificates = chain == null || chain.length == 0
                    ? List.of(certificate)
                    : Arrays.asList(chain);
                choices.add(new CertificateChoice(alias, x509Certificate, certificates));
            }
        }

        certificateChoices = List.copyOf(choices);
    }

    @Override
    public boolean requiresApplicationPin() {
        return false;
    }

    @Override
    public String getDisplayName() {
        return "certificados de Windows";
    }

    @Override
    public List<CertificateChoice> getCertificateChoices() throws Exception {
        requireOpenStore();
        return certificateChoices;
    }

    @Override
    public void selectKeyAlias(String alias) throws Exception {
        requireOpenStore();
        if (certificateChoices.stream().noneMatch(choice -> choice.alias().equals(alias))) {
            throw new Exception("Unknown signing certificate alias.");
        }
        keyAlias = alias;
    }

    @Override
    public X509Certificate getCertificate() throws Exception {
        return (X509Certificate) requireOpenStore().getCertificate(requireSelectedAlias());
    }

    @Override
    public Certificate[] getCertificateChain() throws Exception {
        Certificate[] chain = requireOpenStore().getCertificateChain(requireSelectedAlias());
        return chain == null || chain.length == 0
            ? new Certificate[]{getCertificate()}
            : chain.clone();
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        Key key = requireOpenStore().getKey(requireSelectedAlias(), null);
        if (!(key instanceof PrivateKey privateKey)) {
            throw new Exception("The selected Windows certificate has no accessible private key.");
        }
        return privateKey;
    }

    @Override
    public Provider getSignatureProvider() {
        return signatureProvider;
    }

    @Override
    public void close() {
        keyStore = null;
        signatureProvider = null;
        keyAlias = null;
        certificateChoices = List.of();
    }

    private KeyStore requireOpenStore() throws Exception {
        if (keyStore == null) {
            throw new Exception("Windows-MY has not been opened.");
        }
        return keyStore;
    }

    private String requireSelectedAlias() throws Exception {
        if (keyAlias == null) {
            throw new Exception("No signing certificate has been selected.");
        }
        return keyAlias;
    }
}
