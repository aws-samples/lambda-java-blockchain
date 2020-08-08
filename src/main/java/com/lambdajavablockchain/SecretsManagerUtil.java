package com.lambdajavablockchain;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.*;
import com.lambdajavablockchain.model.AMBConfig;
import com.lambdajavablockchain.exception.EnrollmentNotFoundException;
import com.lambdajavablockchain.exception.SecretNotFoundException;
import com.lambdajavablockchain.model.FabricEnrollment;
import org.apache.commons.io.IOUtils;
import org.hyperledger.fabric.sdk.Enrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Util class to manage Fabric users credentials on AWS Secrets Manager
 *
 */
@Import(AWSSecretsManagerClientBuilder.class)
public class SecretsManagerUtil {

    private static final Logger log = LoggerFactory.getLogger(SecretsManagerUtil.class);

    /**
     * Converts string representation of a private key to PrivateKey Object required by FabricEnrolment
     *
     * @param pkAsString String: Base64 representation of private key
     * @return PrivateKey
     * @throws NoSuchAlgorithmException, InvalidKeySpecException
     */
    public static PrivateKey buildPrivateKeyFromString(String pkAsString) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Base64.getDecoder().decode(pkAsString);

        // Fabric 1.2 PrivateKey uses PKCS8EncodedKeySpec with EC algorithm
        PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(priPKCS8);
    }

    /**
     * Retrieves a secret by name from AWS Secrets Manager
     * @param secretName String: secret name
     * @return String: secret value
     * @throws SecretNotFoundException
     */
    public static String getSecret(String secretName) throws SecretNotFoundException {
        // Create a Secrets Manager client
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
                .withRegion(AMBConfig.REGION)
                .build();

        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(secretName);
        GetSecretValueResult getSecretValueResult = null;

        try {
            getSecretValueResult = client.getSecretValue(getSecretValueRequest);
        } catch (DecryptionFailureException | InvalidParameterException
                | InvalidRequestException | InternalServiceErrorException e) {
            log.warn("Unable to retrieve secret " + secretName);
            throw new SecretNotFoundException("Unable to retrieve secret", e);
        } catch (ResourceNotFoundException e) {
            log.warn("Secret not found in Secrets Manager " + secretName);
            throw new SecretNotFoundException("Secret not found in Secrets Manager", e);
        }

        if (getSecretValueResult.getSecretString() != null) {
            return getSecretValueResult.getSecretString();
        } else {
            return new String(Base64.getDecoder()
                    .decode(getSecretValueResult.getSecretBinary()).array());
        }
    }

    /**
     * Creates FabricEnrollment using user credentials (private key and sign certificate)
     *
     * @param userId String: user id
     * @param orgName String: organisation name
     * @return FabricEnrollment
     * @throws EnrollmentNotFoundException
     */
    public static FabricEnrollment getFabricEnrollment(String userId, String orgName) throws EnrollmentNotFoundException {

        String userPKSecretName = "fabric/orgs/" + orgName + "/" + userId + "/pk";
        String userCertsSecretName = "fabric/orgs/" + orgName + "/" + userId + "/certs";

        try {
            log.debug("Trying to retrieve " + userId + " credentials from AWS Secrets Manager");

            String pkAsString = SecretsManagerUtil.getSecret(userPKSecretName);
            String certString = SecretsManagerUtil.getSecret(userCertsSecretName);

            FabricEnrollment fabricEnrollment = null;

            log.info("Found users credentials in Secrets Manager");
            // Reconstruct PrivateKey from string
            PrivateKey privKey = SecretsManagerUtil.buildPrivateKeyFromString(pkAsString);

            // Create FabricEnrollment with Secrets Manager credentials
            fabricEnrollment = new FabricEnrollment(privKey, certString);
            return fabricEnrollment;
        } catch (SecretNotFoundException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.warn("Credentials not found on Secrets Manager");
            throw new EnrollmentNotFoundException("Fabric credentials not found for user " + userId, e);
        }
    }

    /**
     * Save Enrollment credentials (private Key and sign certificate) to AWS Secrets Manager
     *
     * @param userId String: user id
     * @param orgName String: Organization name
     * @param enrollment Enrollment: Enrollment details
     * @return boolean
     */
    public static boolean storeEnrollmentCredentials(String userId, String orgName, Enrollment enrollment) {

        String userPKSecretName = "fabric/orgs/" + orgName + "/" + userId + "/pk";
        String userCertsSecretName = "fabric/orgs/" + orgName + "/" + userId + "/certs";

        // Save Fabric credentials to Secrets Manager
        SecretsManagerUtil.createSecret(userCertsSecretName, enrollment.getCert());
        SecretsManagerUtil.createSecret(userPKSecretName,
                Base64.getEncoder().encodeToString(enrollment.getKey().getEncoded()));
        return true;
    }

    /**
     * Creates a secret on AWS Secrets Manager
     * @param secretName String: secret name
     * @param value String: secret value
     * @return boolean
     */
    public static boolean createSecret(String secretName, String value) {
        // Create a Secrets Manager client
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
                .withRegion(AMBConfig.REGION)
                .build();
        CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                .withName(secretName)
                .withSecretString(value);

        try {
            client.createSecret(createSecretRequest);
            return true;
        } catch (ResourceExistsException | InvalidRequestException e) {
            return false;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Reads TLS certificate from resources folder
     * @param certFileName certificate file name
     * @return String
     */
    public static String readCert(String certFileName) throws IOException {
        String certificate = null;
        InputStream certIs = null;
        try {
            certIs = new ClassPathResource(certFileName).getInputStream();
            certificate = new String(IOUtils.toByteArray(certIs), "UTF-8");
        } finally {
            if(certIs != null) {
                certIs.close();
            }
        }
        return certificate;
    }
}

