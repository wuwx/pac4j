package org.pac4j.saml.crypto;

import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialResolver;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.signature.KeyInfo;

/**
 * Defines operations required to provide and resolve credentials.
 *
 * @author Misagh Moayyed
 * @since 1.7
 */
public interface CredentialProvider {

    /**
     * <p>getKeyInfo.</p>
     *
     * @return a {@link org.opensaml.xmlsec.signature.KeyInfo} object
     */
    KeyInfo getKeyInfo();

    /**
     * <p>getCredentialResolver.</p>
     *
     * @return a {@link org.opensaml.security.credential.CredentialResolver} object
     */
    CredentialResolver getCredentialResolver();

    /**
     * <p>getKeyInfoCredentialResolver.</p>
     *
     * @return a {@link org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver} object
     */
    KeyInfoCredentialResolver getKeyInfoCredentialResolver();

    /**
     * <p>getKeyInfoGenerator.</p>
     *
     * @return a {@link org.opensaml.xmlsec.keyinfo.KeyInfoGenerator} object
     */
    KeyInfoGenerator getKeyInfoGenerator();

    /**
     * <p>getCredential.</p>
     *
     * @return a {@link org.opensaml.security.credential.Credential} object
     */
    Credential getCredential();
}
