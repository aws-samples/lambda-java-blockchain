package com.lambdajavablockchain.service;

import com.lambdajavablockchain.SecretsManagerUtil;
import com.lambdajavablockchain.exception.AppException;
import com.lambdajavablockchain.exception.ManagedBlockchainServiceException;
import com.lambdajavablockchain.exception.EnrollmentNotFoundException;
import com.lambdajavablockchain.model.*;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.hyperledger.fabric_ca.sdk.exception.EnrollmentException;
import org.hyperledger.fabric_ca.sdk.exception.RegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Managed Blockchain service that interacts with the Fabric SDK to enroll admin, enroll user & query/invoke chaincode
 *
 */
@Component
public class ManagedBlockchainService {

    private HFCAClient caClient;
    private HFClient client;
    private Channel channel;
    private String ambTlsCertAsString;

    private static final Logger log = LoggerFactory.getLogger(ManagedBlockchainService.class);

    public ManagedBlockchainService() {}

    public void setupClient() throws AppException, ManagedBlockchainServiceException {
        if(this.caClient != null && this.client != null) {
            return;
        }
        try {
            log.info("Setting up CA Client and Client");
            // Set CA details
            this.ambTlsCertAsString = SecretsManagerUtil.readCert(AMBConfig.AMB_CERT_PATH);
            Properties caProperties = new Properties();
            caProperties.put("pemBytes", ambTlsCertAsString.getBytes());

            // create HLF CA Client
            this.caClient = createHFCAClient(caProperties);

            // create HLF Client
            this.client = createHFClient();
        } catch (AppException e) {
            log.error("Error setting up client, ManagedBlockchainService.setupClient() failed - " + e.getMessage());
            e.printStackTrace();
            throw new ManagedBlockchainServiceException("Error setting up client - " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("Could not find Managed Blockchain TLS certificate - " + e.getMessage());
            throw new AppException("Managed Blockchain TLS certificate not found", e);
        }
    }

    /**
     * Function to enroll admin
     *
     */
    private FabricUser enrollAdmin() throws AppException, ManagedBlockchainServiceException {
        if (client == null || caClient == null) {
            log.error("Client/CA Client not initialized. Run ManagedBlockchainService.setupClient() first");
            throw new ManagedBlockchainServiceException("Client/CA Client not initialized!");
        }

        try {
            // Retrieve admin User Context
            FabricUser fabricUser = getAdmin(caClient);

            // Set client to act on behalf of adminUser
            client.setUserContext(fabricUser);
            log.info("Using admin user context");
            return fabricUser;
        } catch (InvalidArgumentException | org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException
                | EnrollmentException e) {
            log.error("Error enrolling Admin user - " + e.getMessage());
            e.printStackTrace();
            throw new AppException("Error enrolling Admin user - " + e.getMessage(), e);
        }
    }

    /**
     * Set User context by using already enrolled user
     *
     * @param userId String: userId
     */
    public void setUser(String userId) throws Exception {
        // Check if user is has enrollment credentials on AWS Secrets Manager
        Enrollment enrollment = SecretsManagerUtil.getFabricEnrollment(userId, AMBConfig.ORG1);

        // create Fabric user context
        FabricUser fabricUser = new FabricUser(userId, AMBConfig.ORG1, AMBConfig.ORG1_MSP, enrollment);

        // check that the client has been properly setup
        if (client == null) {
            log.error("Client not initialized. Run ManagedBlockchainService.setupClient() first");
            throw new ManagedBlockchainServiceException("Client not initialized!");
        }

        // Set client to act on behalf of userId
        client.setUserContext(fabricUser);
        log.info("Using " + userId + " user context");
    }

    /**
     * Enroll a Fabric user, if user is already enrolled it retrieves user context from
     * AWS Secrets Manager
     *
     * @param userId   String: userId
     * @param password String: password
     */
    public void enrollUser(String userId, String password) throws Exception {
        try {
            // Check if user has enrollment credentials on AWS Secrets Manager
            Enrollment enrollment = SecretsManagerUtil.getFabricEnrollment(userId, AMBConfig.ORG1);
            log.info("User is already enrolled!");
        } catch (EnrollmentNotFoundException e) {
            // User is enrolling for the first time
            log.info("Enrollment not found for user, enrolling user ...");
            // Enroll admin and set admin context, we will need admin context to enroll a new user user
            FabricUser adminUser = enrollAdmin();
            // Next, enroll user
            enrollUserToCA(caClient, adminUser, userId, password);
        }
    }

    /**
     * Start channel initialization
     *
     */
    public void initChannel() throws AppException {
        // Initialize Channel
        log.info("Initializing channel ...");
        this.channel = initializeChannel(client);
        log.info("Channel initialized!");
    }

    public Channel getChannel() {
        return channel;
    }

    public HFClient getClient() {
        return client;
    }

    /**
     * Initialize Fabric channel
     *
     * @param client The HF Client
     * @return Channel
     */
    private Channel initializeChannel(HFClient client) throws AppException {
        if (this.channel != null && this.channel.isInitialized()) {
            return channel;
        }
        try {
            // Read Managed Blockchain TLS certificate from resources folder
            Properties properties = new Properties();
            if (ambTlsCertAsString.isEmpty()) {
                properties.put("pemBytes", SecretsManagerUtil.readCert(AMBConfig.AMB_CERT_PATH));
            } else {
                properties.put("pemBytes", ambTlsCertAsString.getBytes());
            }

            properties.setProperty("sslProvider", "openSSL");
            properties.setProperty("negotiationType", "TLS");

            // Configure Peer
            Peer peer = client.newPeer(AMBConfig.ORG1_PEER_0, AMBConfig.ORG1_PEER_0_URL, properties);
            // Configure Orderer
            Orderer orderer = client.newOrderer(AMBConfig.ORDERER_NAME, AMBConfig.ORDERER_URL, properties);
            // Configure Channel
            Channel channel = client.newChannel(AMBConfig.CHANNEL_NAME);

            channel.addPeer(peer);
            channel.addOrderer(orderer);
            channel.initialize();

            return channel;
        } catch (InvalidArgumentException | TransactionException e) {
            e.printStackTrace();
            log.error("Unable to initialize channel - " + e.getMessage());
            throw new AppException("Unable to initialize channel", e);
        } catch (IOException e) {
            log.error("Could not find Managed Blockchain TLS certificate - " + e.getMessage());
            e.printStackTrace();
            throw new AppException("Managed Blockchain TLS certificate not found", e);
        }
    }

    /**
     * Register and enroll user with provided {@code userId/userPassword}
     * Upon successful enrollment, user credentials will be saved on AWS Secrets Manager
     *
     * @param caClient  HFCAClient: The fabric-ca client.
     * @param registrar FabricUser: The registrar to be used.
     * @param userId    String: The user id.
     * @return Enrollment instance
     */
    private Enrollment enrollUserToCA(HFCAClient caClient, FabricUser registrar,
                                      String userId, String userPassword) throws Exception {
        try {
            log.info("Attempting to enroll user " + userId + " ...");
            RegistrationRequest registrationRequest = new RegistrationRequest(userId, AMBConfig.ORG1);
            registrationRequest.setSecret(userPassword);

            // Register and enroll user
            String enrollmentSecret = caClient.register(registrationRequest, registrar);
            Enrollment userEnrollment = caClient.enroll(userId, enrollmentSecret);
            log.info("Userid:" + userId + " successfully enrolled");

            // Save credentials on AWS Secrets Manager
            SecretsManagerUtil.storeEnrollmentCredentials(userId, AMBConfig.ORG1, userEnrollment);

            log.info("Userid:" + userId + " credentials saved on Secrets Manager");
            return userEnrollment;
        } catch (org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException | RegistrationException | EnrollmentException e) {
            log.error("Error enrolling user to CA - " + e.getMessage());
            e.printStackTrace();
            throw new AppException("Error enrolling user to CA", e);
        }
    }

    /**
     * Enroll admin into Fabric CA using {@code admin/adminpwd} credentials.
     * If admin's certificates are already present on AWS Secrets Manager, enrollment will be skipped and
     * Admin user context will be reconstructed using credentials from Secrets Manager.
     *
     * @param hfcaClient HFCAClient: The Fabric CA client
     * @return FabricUser instance
     */
    private FabricUser getAdmin(HFCAClient hfcaClient) throws EnrollmentException, org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException {
        try {
            // Try to build enrollment using AWS Secrets Manager credentials
            FabricEnrollment fabricEnrollment = SecretsManagerUtil.getFabricEnrollment(AMBConfig.ADMINUSER, AMBConfig.ORG1);

            // Create Admin user context with existing credentials
            FabricUser adminUserContext = new FabricUser(AMBConfig.ADMINUSER, AMBConfig.ORG1,
                    AMBConfig.ORG1_MSP, fabricEnrollment);
            log.info("Admin user context reconstructed from Secrets Manager");
            return adminUserContext;
        } catch (EnrollmentNotFoundException e) {
            // If admin has not yet been enrolled, enroll admin once and save credentials
            log.info("No secret found in Secrets Manager, enrolling admin");

            // Enroll Admin first
            Enrollment adminEnrollment = hfcaClient.enroll(AMBConfig.ADMINUSER, AMBConfig.ADMINPWD);
            FabricUser adminUserContext = new FabricUser(AMBConfig.ADMINUSER, AMBConfig.ORG1,
                    AMBConfig.ORG1_MSP, adminEnrollment);
            log.info("Admin successfully enrolled");

            // Save credentials on AWS Secrets Manager
            SecretsManagerUtil.storeEnrollmentCredentials(AMBConfig.ADMINUSER, AMBConfig.ORG1, adminEnrollment);

            log.info("Admin credentials saved on Secrets Manager");
            return adminUserContext;
        }
    }

    /**
     * Create HLF client
     *
     * @return HFClient instance.
     */
    private static HFClient createHFClient() throws AppException {
        try {
            CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
            HFClient client = HFClient.createNewInstance();
            client.setCryptoSuite(cryptoSuite);
            return client;
        } catch (IllegalAccessException | InstantiationException | ClassNotFoundException | CryptoException
                | InvalidArgumentException | NoSuchMethodException | InvocationTargetException e) {
            log.error("Error creating Fabric client - " + e.getMessage());
            e.printStackTrace();
            throw new AppException("Error creating Fabric Client", e);
        }
    }

    /**
     * Create HLF CA client
     *
     * @param caClientProperties String: The Fabric CA client properties.
     * @return HFCAClient instance
     */
    private HFCAClient createHFCAClient(Properties caClientProperties) throws AppException {
        try {
            CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
            HFCAClient caClient = HFCAClient.createNewInstance(AMBConfig.CA_ORG1_URL, caClientProperties);
            caClient.setCryptoSuite(cryptoSuite);
            return caClient;
        } catch (IllegalAccessException | InstantiationException | ClassNotFoundException | CryptoException |
                InvalidArgumentException | NoSuchMethodException | InvocationTargetException | MalformedURLException e) {
            log.error("Error creating Fabric CA client - " + e.getMessage());
            e.printStackTrace();
            throw new AppException("Error creating Fabric CA Client", e);
        }
    }

    /**
     * Query chaincode by chaincodeName, functionName and arguments provided
     *
     * @param hfClient      HFClient: Fabric Client instance
     * @param channel       Channel: Channel instance
     * @param chaincodeName String: chaincode to query
     * @param functionName  String: function to query
     * @param args          String: argument for the query function
     * @return String: query response
     */
    public String queryChaincode(HFClient hfClient, Channel channel, String chaincodeName, String functionName,
                                 String args) throws ManagedBlockchainServiceException, ProposalException, InvalidArgumentException {

        if (channel == null || hfClient == null) {
            log.error("Channel/Client not initialized. Run ManagedBlockchainService.initChannel() first");
            throw new ManagedBlockchainServiceException("Channel/Client not initialized!");
        }
        QueryByChaincodeRequest qpr = hfClient.newQueryProposalRequest();
        // Chaincode Version is omitted, it can be added if required
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincodeName).build();
        qpr.setChaincodeID(chaincodeID);
        qpr.setFcn(functionName);
        String[] arguments = {args};
        qpr.setArgs(arguments);

        // Query the chaincode
        Collection<ProposalResponse> res = channel.queryByChaincode(qpr);

        String result = "";
        // Retrieve the query response
        for (ProposalResponse pres : res) {
            result = new String(pres.getChaincodeActionResponsePayload());
            log.info("Query result: " + result);
        }

        return result;
    }

    /**
     * Invoke chaincode by chaincodeName, functionName and argument list
     *
     * @param hfClient      HFClient: HLF client instance
     * @param channel       Channel: Channel instance
     * @param chainCodeName String: chaincode to invoke
     * @param functionName  String: function to invoke
     * @param arguments     String[]: list of arguments for chaincode invocation
     */
    public void invokeChaincode(HFClient hfClient, Channel channel, String chainCodeName, String functionName,
                                String[] arguments) throws ManagedBlockchainServiceException, InvalidArgumentException {

        if (channel == null || hfClient == null) {
            log.error("Channel/Client not initialized. Run ManagedBlockchainService.initChannel() first");
            throw new ManagedBlockchainServiceException("Channel/Client not initialized!");
        }
        // Set chaincdoe name, function and arguments
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chainCodeName).build();
        TransactionProposalRequest invokeRequest = hfClient.newTransactionProposalRequest();
        invokeRequest.setChaincodeID(chaincodeID);
        invokeRequest.setFcn(functionName);
        invokeRequest.setArgs(arguments);
        invokeRequest.setProposalWaitTime(2000);

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        try {
            // Send transaction proposal to all peers
            Collection<ProposalResponse> responses = channel.sendTransactionProposal(invokeRequest);

            // Process responses from transaction proposal
            for (ProposalResponse response : responses) {
                String stringResponse = new String(response.getChaincodeActionResponsePayload());
                log.info("Invoke status:" + response.getStatus() + " result:" + stringResponse);

                if (response.getStatus() == ChaincodeResponse.Status.SUCCESS) {
                    log.info("Received successful transaction proposal response txId:"
                            + response.getTransactionID() + " from peer: " + response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                    log.error("Received unsuccessful transaction proposal response");
                }
            }

            if (failed.size() > 0) {
                log.error("Failed to send Proposal and receive successful proposal responses");
                throw new RuntimeException("Proposal error");
            }
            // Send transaction to Orderer
            CompletableFuture<BlockEvent.TransactionEvent> cf = channel.sendTransaction(responses);
            CompletableFuture<Void> future = cf
                    .thenAccept((s) -> log.info("Invoke Completed. Block nb:" + s.getBlockEvent().getBlockNumber()));

        } catch (ProposalException ex) {
            log.error("Proposal exception " + ex.getMessage());
            throw new RuntimeException("Proposal exception ", ex);
        }
    }
}
