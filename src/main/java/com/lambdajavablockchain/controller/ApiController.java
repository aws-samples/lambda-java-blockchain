package com.lambdajavablockchain.controller;

import com.lambdajavablockchain.exception.AppException;
import com.lambdajavablockchain.exception.EnrollmentNotFoundException;
import com.lambdajavablockchain.exception.ManagedBlockchainServiceException;
import com.lambdajavablockchain.model.*;
import com.lambdajavablockchain.service.ManagedBlockchainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.validation.Valid;

@RestController
@EnableWebMvc
@Import({ManagedBlockchainService.class})
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    ManagedBlockchainService service;

    /**
     * Enroll a new Fabric user
     *
     * @return
     */
    @RequestMapping(path = "/enroll-lambda-user", method = RequestMethod.POST)
    public ResponseEntity<?> enrollUser() {
        try {
            log.debug("Enrolling user - user:" + AMBConfig.LAMBDAUSER);

            // Register and enroll user to Fabric CA
            service.setupClient();
            service.enrollUser(AMBConfig.LAMBDAUSER, AMBConfig.LAMBDAUSERPWD);

            return new ResponseEntity<>(AMBConfig.LAMBDAUSER + " enrolled successfully", HttpStatus.OK);
        } catch (AppException e) {
            log.error("Error while enrolling user - userId:" + AMBConfig.LAMBDAUSER);
            return new ResponseEntity<>("Error while enrolling user - " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ManagedBlockchainServiceException e) {
            log.error("Error while enrolling user, ManagedBlockchainService startup failed - " + e.getMessage());
            return new ResponseEntity<>("Error while enrolling user, ManagedBlockchainService startup failed - "
                    + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error while enrolling user - userId:" + AMBConfig.LAMBDAUSER);
            e.printStackTrace();
            return new ResponseEntity<>("Error while enrolling user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generic endpoint to query any function on any chaincode.
     *
     * @param chaincodeName Name of the chaincode
     * @param functionName Name of the function to query
     * @param args (optional) argument for the function to query
     * @return
     */
    @RequestMapping(path = "/query", method = RequestMethod.GET)
    public ResponseEntity<?> query(@RequestParam String chaincodeName,
                                   @RequestParam String functionName,
                                   @RequestParam(required = false) String args) {
        try {
            if (args == null)
                args = "";

            log.debug("Querying chaincode - chaincodeName:" + chaincodeName +
                                            "functionName:" + functionName +
                                            "args:" + args);

            service.setupClient();
            // First retrieve LambdaUser's credentials and set user context
            service.setUser(AMBConfig.LAMBDAUSER);
            service.initChannel();

            String res = service.queryChaincode(service.getClient(), service.getChannel(), chaincodeName, functionName, args);
            return new ResponseEntity<>(res, HttpStatus.OK);
        } catch (EnrollmentNotFoundException | AppException e){
            log.error("Error while querying chaincode - " + e.getMessage());
            return new ResponseEntity<>("Error while querying chaincode - " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ManagedBlockchainServiceException e) {
            log.error("Error while querying chaincode, " + e.getMessage());
            return new ResponseEntity<>("Error while querying chaincode, ManagedBlockchainService startup failed - "
                    + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error while querying - function:" + functionName + " chaincode:" + chaincodeName);
            e.printStackTrace();
            return new ResponseEntity<>("Error while querying chaincode", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generic endpoint to invoke any function on any chaincode
     *
     * @param invokeRequest InvokeRequest object containing:
     *                      - chaincodeName: name of the chaincode
     *                      - functionName: function to invoke
     *                      - argsList (optional): list of arguments for the function to invoke
     * @return
     */
    @RequestMapping(path = "/invoke", method = RequestMethod.POST)
    public ResponseEntity<?> invoke(@RequestBody @Valid InvokeRequest invokeRequest) {
        try {
            log.debug("Invoking chaincode with payload:" + invokeRequest.toString());

            service.setupClient();
            // First retrieve LambdaUser's credentials and set user context
            service.setUser(AMBConfig.LAMBDAUSER);
            service.initChannel();

            // build arguments list required by the chaincode
            String[] arguments = invokeRequest.getArgList().stream().toArray(String[]::new);

            service.invokeChaincode(service.getClient(), service.getChannel(),
                    invokeRequest.getChaincodeName(),
                    invokeRequest.getFunctionName(),
                    arguments);

            return new ResponseEntity<>("Invoke successful", HttpStatus.ACCEPTED);
        } catch (EnrollmentNotFoundException | AppException e){
            log.error("Error while invoking chaincode - " + e.getMessage());
            return new ResponseEntity<>("Error while invoking chaincode - " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ManagedBlockchainServiceException e) {
            log.error("Error while invoking chaincode, ManagedBlockchainService startup failed - " + e.getMessage());
            return new ResponseEntity<>("Error while invoking chaincode, ManagedBlockchainService startup failed - "
                    + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error while invoking - function:" + invokeRequest.getFunctionName() +
                                              "chaincode:" + invokeRequest.getFunctionName());
            e.printStackTrace();
            return new ResponseEntity<>("Error while invoking chaincode", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Query a car by carId on Fabcar chaincode
     *
     * @param carId The id of the car to query
     * @return Car json object
     */
    @RequestMapping(path = "/cars/{carId}", method = RequestMethod.GET)
    public ResponseEntity<?> queryFabcar(@PathVariable(name = "carId") String carId) {
        try {
            log.debug("Querying car by carId:" + carId);

            service.setupClient();
            // First retrieve LambdaUser's credentials and set user context
            service.setUser(AMBConfig.LAMBDAUSER);
            service.initChannel();

            // query chaincode
            String res = service.queryChaincode(service.getClient(), service.getChannel(),
                    "fabcar", "queryCar", carId);

            // convert the response into Car object
            ObjectMapper objectMapper = new ObjectMapper();
            Car car = objectMapper.readValue(res, Car.class);
            car.setId(carId);
            return new ResponseEntity<>(car, HttpStatus.OK);
        } catch (EnrollmentNotFoundException | AppException e){
            log.error("Error while querying - " + e.getMessage());
            return new ResponseEntity<>("Error querying car - " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ManagedBlockchainServiceException e) {
            log.error("Error querying car, ManagedBlockchainService startup failed - " + e.getMessage());
            return new ResponseEntity<>("Error querying car, ManagedBlockchainService startup failed - "
                    + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error while querying - function:queryCar chaincode:fabcar");
            e.printStackTrace();
            return new ResponseEntity<>("Error querying car, chaincode query failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Inserts a new Car by invoking the `createCar` function in Fabcar chaincode
     *
     * @param car json object to insert
     * @return
     */
    @RequestMapping(path = "/cars", method = RequestMethod.POST)
    public ResponseEntity<?> invokeFabcar(@RequestBody @Valid Car car) {
        try {
            log.debug("Inserting new Car:" + car.toString());

            service.setupClient();
            // First retrieve LambdaUser's credentials and set user context
            service.setUser(AMBConfig.LAMBDAUSER);
            service.initChannel();

            // build arguments list required by the chaincode
            String[] arguments = {car.getId(), car.getMake(), car.getModel(), car.getColour(), car.getOwner()};

            // invoke createCar function on fabcar chaincode
            service.invokeChaincode(service.getClient(), service.getChannel(), "fabcar",
                    "createCar", arguments);

            return new ResponseEntity<>("Car created successfully", HttpStatus.ACCEPTED);
        } catch (EnrollmentNotFoundException | AppException e){
            log.error("Error creating car - " + e.getMessage());
            return new ResponseEntity<>("Error creating car - " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ManagedBlockchainServiceException e) {
            log.error("Error creating car, ManagedBlockchainService startup failed - " + e.getMessage());
            return new ResponseEntity<>("Error creating car, ManagedBlockchainService startup failed - "
                    + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Error while invoking - function:createCar chaincode:fabcar");
            e.printStackTrace();
            return new ResponseEntity<>("Error creating car, chaincode invocation failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}