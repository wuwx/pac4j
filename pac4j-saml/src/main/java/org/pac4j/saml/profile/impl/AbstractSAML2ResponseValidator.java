package org.pac4j.saml.profile.impl;


import lombok.val;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.net.URIComparator;
import net.shibboleth.shared.resolver.CriteriaSet;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.saml.common.binding.security.impl.MessageReplaySecurityHandler;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.criterion.ProtocolCriterion;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.criteria.UsageCriterion;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureTrustEngine;
import org.pac4j.core.logout.handler.SessionLogoutHandler;
import org.pac4j.saml.context.SAML2MessageContext;
import org.pac4j.saml.credentials.SAML2AuthenticationCredentials;
import org.pac4j.saml.crypto.SAML2SignatureTrustEngineProvider;
import org.pac4j.saml.exceptions.*;
import org.pac4j.saml.profile.api.SAML2ResponseValidator;
import org.pac4j.saml.replay.ReplayCacheProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * The abstract class for all SAML response validators.
 *
 * @author Jerome Leleu
 * @since 3.4.0
 */
public abstract class AbstractSAML2ResponseValidator implements SAML2ResponseValidator {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final SAML2SignatureTrustEngineProvider signatureTrustEngineProvider;

    protected final URIComparator uriComparator;

    protected final Decrypter decrypter;

    protected final SessionLogoutHandler logoutHandler;

    protected final ReplayCacheProvider replayCache;

    /* maximum skew in seconds between SP and IDP clocks */
    protected long acceptedSkew = 120;

    /**
     * <p>Constructor for AbstractSAML2ResponseValidator.</p>
     *
     * @param signatureTrustEngineProvider a {@link org.pac4j.saml.crypto.SAML2SignatureTrustEngineProvider} object
     * @param decrypter a {@link org.opensaml.saml.saml2.encryption.Decrypter} object
     * @param logoutHandler a {@link org.pac4j.core.logout.handler.SessionLogoutHandler} object
     * @param replayCache a {@link org.pac4j.saml.replay.ReplayCacheProvider} object
     * @param uriComparator a {@link net.shibboleth.shared.net.URIComparator} object
     */
    protected AbstractSAML2ResponseValidator(final SAML2SignatureTrustEngineProvider signatureTrustEngineProvider,
                                             final Decrypter decrypter, final SessionLogoutHandler logoutHandler,
                                             final ReplayCacheProvider replayCache, final URIComparator uriComparator) {
        this.signatureTrustEngineProvider = signatureTrustEngineProvider;
        this.decrypter = decrypter;
        this.logoutHandler = logoutHandler;
        this.replayCache = replayCache;
        this.uriComparator = uriComparator;
    }

    /**
     * Validates that the response is a success.
     *
     * @param status the response status.
     */
    protected void validateSuccess(final Status status) {
        if (status == null || status.getStatusCode() == null) {
            throw new SAMLException("Missing response status or status code");
        }

        var statusValue = status.getStatusCode().getValue();
        if (!StatusCode.SUCCESS.equals(statusValue)) {
            val statusMessage = status.getStatusMessage();
            if (statusMessage != null) {
                statusValue += " / " + statusMessage.getValue();
            }
            throw new SAMLException("Response is not success ; actual " + statusValue);
        }
    }

    /**
     * <p>validateSignatureIfItExists.</p>
     *
     * @param signature a {@link org.opensaml.xmlsec.signature.Signature} object
     * @param context a {@link org.pac4j.saml.context.SAML2MessageContext} object
     * @param engine a {@link org.opensaml.xmlsec.signature.support.SignatureTrustEngine} object
     */
    protected void validateSignatureIfItExists(final Signature signature, final SAML2MessageContext context,
                                               final SignatureTrustEngine engine) {
        if (signature != null) {
            val entityId = context.getSAMLPeerEntityContext().getEntityId();
            validateSignature(signature, entityId, engine);
            context.getSAMLPeerEntityContext().setAuthenticated(true);
            logger.debug("Successfully validated signature for entity id {}", entityId);
        } else {
            logger.debug("Cannot locate a signature from the message; skipping validation");
        }
    }

    /**
     * Validate the given digital signature by checking its profile and value.
     *
     * @param signature   the signature
     * @param idpEntityId the idp entity id
     * @param trustEngine the trust engine
     */
    protected void validateSignature(final Signature signature, final String idpEntityId,
                                     final SignatureTrustEngine trustEngine) {


        val validator = new SAMLSignatureProfileValidator();
        try {
            logger.debug("Validating profile signature for entity id {}", idpEntityId);
            validator.validate(signature);
        } catch (final SignatureException e) {
            throw new SAMLSignatureValidationException("SAMLSignatureProfileValidator failed to validate signature", e);
        }

        val criteriaSet = new CriteriaSet();
        criteriaSet.add(new UsageCriterion(UsageType.SIGNING));
        criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
        criteriaSet.add(new ProtocolCriterion(SAMLConstants.SAML20P_NS));
        criteriaSet.add(new EntityIdCriterion(idpEntityId));
        final boolean valid;
        try {
            logger.debug("Validating signature via trust engine for entity id {}", idpEntityId);
            valid = trustEngine.validate(signature, criteriaSet);
        } catch (final SecurityException e) {
            throw new SAMLSignatureValidationException("An error occurred during signature validation", e);
        }
        if (!valid) {
            throw new SAMLSignatureValidationException("Signature is not trusted");
        }
    }

    /**
     * <p>validateIssuerIfItExists.</p>
     *
     * @param isser a {@link org.opensaml.saml.saml2.core.Issuer} object
     * @param context a {@link org.pac4j.saml.context.SAML2MessageContext} object
     */
    protected void validateIssuerIfItExists(final Issuer isser, final SAML2MessageContext context) {
        if (isser != null) {
            validateIssuer(isser, context);
        }
    }

    /**
     * Validate issuer format and value.
     *
     * @param issuer  the issuer
     * @param context the context
     */
    protected void validateIssuer(final Issuer issuer, final SAML2MessageContext context) {
        if (issuer.getFormat() != null && !issuer.getFormat().equals(NameIDType.ENTITY)) {
            throw new SAMLIssuerException("Issuer type is not entity but " + issuer.getFormat());
        }

        val entityId = context.getSAMLPeerEntityContext().getEntityId();
        logger.debug("Comparing issuer {} against {}", issuer.getValue(), entityId);
        if (entityId == null || !entityId.equals(issuer.getValue())) {
            throw new SAMLIssuerException("Issuer " + issuer.getValue() + " does not match idp entityId " + entityId);
        }
    }

    /**
     * <p>validateIssueInstant.</p>
     *
     * @param issueInstant a {@link java.time.Instant} object
     */
    protected void validateIssueInstant(final Instant issueInstant) {
        if (!isIssueInstantValid(issueInstant)) {
            throw new SAMLIssueInstantException("Issue instant is too old or in the future");
        }
    }

    /**
     * <p>isIssueInstantValid.</p>
     *
     * @param issueInstant a {@link java.time.Instant} object
     * @return a boolean
     */
    protected boolean isIssueInstantValid(final Instant issueInstant) {
        return isDateValid(issueInstant, 0);
    }

    /**
     * <p>isDateValid.</p>
     *
     * @param issueInstant a {@link java.time.Instant} object
     * @param interval a long
     * @return a boolean
     */
    protected boolean isDateValid(final Instant issueInstant, final long interval) {
        val now = ZonedDateTime.now(ZoneOffset.UTC);
        val before = now.plusSeconds(acceptedSkew);
        val after = now.minusSeconds(acceptedSkew + interval);

        val issueInstanceUtc = ZonedDateTime.ofInstant(issueInstant, ZoneOffset.UTC);

        val isDateValid = issueInstanceUtc.isBefore(before) && issueInstanceUtc.isAfter(after);
        if (!isDateValid) {
            logger.warn("interval={},before={},after={},issueInstant={}", interval, before, after, issueInstanceUtc);
        }
        return isDateValid;
    }

    /**
     * <p>verifyEndpoint.</p>
     *
     * @param endpoints a {@link java.util.List} object
     * @param destination a {@link java.lang.String} object
     * @param isDestinationMandatory a boolean
     */
    protected void verifyEndpoint(final List<String> endpoints, final String destination, final boolean isDestinationMandatory) {
        if (destination == null && !isDestinationMandatory) {
            return;
        }
        if (destination == null) {
            throw new SAMLEndpointMismatchException("SAML configuration does not allow response Destination to be null");
        }

        val verified = endpoints.stream()
            .allMatch(endpoint -> compareEndpoints(destination, endpoint));
        if (!verified) {
            throw new SAMLEndpointMismatchException("Intended destination " + destination
                + " doesn't match any of the endpoint URLs  "
                + endpoints);
        }
    }

    /**
     * <p>compareEndpoints.</p>
     *
     * @param destination a {@link java.lang.String} object
     * @param endpoint a {@link java.lang.String} object
     * @return a boolean
     */
    protected boolean compareEndpoints(final String destination, final String endpoint) {
        try {
            return uriComparator.compare(destination, endpoint);
        } catch (final Exception e) {
            throw new SAMLEndpointMismatchException(e);
        }
    }

    /**
     * <p>verifyMessageReplay.</p>
     *
     * @param context a {@link org.pac4j.saml.context.SAML2MessageContext} object
     */
    protected void verifyMessageReplay(final SAML2MessageContext context) {
        if (replayCache == null) {
            logger.warn("No replay cache specified, skipping replay verification");
            return;
        }

        try {
            val messageReplayHandler = new MessageReplaySecurityHandler();
            messageReplayHandler.setExpires(Duration.ofMillis(acceptedSkew * 1000));
            messageReplayHandler.setReplayCache(replayCache.get());
            messageReplayHandler.initialize();
            messageReplayHandler.invoke(context.getMessageContext());
        } catch (final ComponentInitializationException e) {
            throw new SAMLException(e);
        } catch (final MessageHandlerException e) {
            throw new SAMLReplayException(e);
        }
    }

    /**
     * Decrypts an EncryptedID, using a decrypter.
     *
     * @param encryptedId The EncryptedID to be decrypted.
     * @param decrypter   The decrypter to use.
     * @return Decrypted ID or {@code null} if any input is {@code null}.
     * @throws org.pac4j.saml.exceptions.SAMLException If the input ID cannot be decrypted.
     */
    protected NameID decryptEncryptedId(final EncryptedID encryptedId, final Decrypter decrypter) throws SAMLException {
        if (encryptedId == null) {
            return null;
        }
        if (decrypter == null) {
            logger.warn("Encrypted attributes returned, but no keystore was provided.");
            return null;
        }

        try {
            logger.debug("Decrypting name id {}", encryptedId);
            val decryptedId = (NameID) decrypter.decrypt(encryptedId);
            return decryptedId;
        } catch (final DecryptionException e) {
            throw new SAMLNameIdDecryptionException("Decryption of an EncryptedID failed.", e);
        }
    }

    /**
     * <p>computeSloKey.</p>
     *
     * @param sessionIndex a {@link java.lang.String} object
     * @param nameId a {@link org.pac4j.saml.credentials.SAML2AuthenticationCredentials.SAMLNameID} object
     * @return a {@link java.lang.String} object
     */
    protected String computeSloKey(final String sessionIndex, final SAML2AuthenticationCredentials.SAMLNameID nameId) {
        if (sessionIndex != null) {
            return sessionIndex;
        }

        if (nameId != null) {
            return nameId.getValue();
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    public final void setAcceptedSkew(final long acceptedSkew) {
        this.acceptedSkew = acceptedSkew;
    }
}
