package info.hypocrisy.model;

import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import se.pitch.prti1516e.handles.AttributeHandleValueMapImpl;
import se.pitch.prti1516e.time.HLAfloat64IntervalImpl;
import se.pitch.prti1516e.time.HLAfloat64TimeImpl;

import java.util.*;
import java.net.URL;
/**
 * Created by Hypocrisy on 3/23/2016.
 * This class implements a NullFederateAmbassador.
 */
public class Federate extends NullFederateAmbassador implements Runnable {
    public boolean isPhysicalDevice() {
        return isPhysicalDevice;
    }
    public void setIsPhysicalDevice(boolean isPhysicalDevice) {
        this.isPhysicalDevice = isPhysicalDevice;
    }
    private boolean isPhysicalDevice = false;
    private volatile boolean state = true;              // if false, a thread should be destroyed.
    public void setState(boolean state) {
        this.state = state;
    }
    private volatile boolean status = false;            // if false, a thread should pause, or we can say it doing nothing.
    public void setStatus(boolean status) {
        this.status = status;
    }
    private boolean isRegulating = false;               // flag to mark if this federate is Regulating
    private boolean isConstrained = false;              // flag to mark if this federate is Constrained

    private RTIambassador rtiAmbassador;
    /**********************
     * All interaction class handle and their parameters' handle
     **********************/
    private ParameterHandle cruiseMissileStatus;
    private ParameterHandle early_warningRadarStatus;
    private ParameterHandle early_warningRadarPosition;
    private ParameterHandle strategyOfMissionDistribution;
    private ParameterHandle anti_aircraftStatus;
    private ParameterHandle route;
    private ParameterHandle trackingRadarForEnemyStatus;
    private ParameterHandle trackingRadarForEnemyPosition;
    private ParameterHandle trackingRadarForOurStatus;
    private ParameterHandle trackingRadarForOurPosition;
    private ParameterHandle communicationMessage;
    private ParameterHandle communicationSender;

    private InteractionClassHandle[] interactionClasses;
    private Map<InteractionClassHandle,List<ParameterHandle>> mapInteractionParameters = new HashMap<>();
    /**********************
     * All object instance handle and their attributes' handle
     **********************/
    private ObjectClassHandle[] objectClassHandles;
    private Map<ObjectClassHandle,List<AttributeHandle>> mapObjectAttributes = new HashMap<>();
    private ObjectInstanceHandle objectInstanceId;

    private String name;
    private volatile boolean reservationComplete;
    private volatile boolean reservationSucceeded;
    private final Object reservationSemaphore = new Object();

    /**********************
     * Sync points variables.
     **********************/
    private final String SYNC_POINT = "ReadyToRun";
    private volatile boolean isRegisterSyncPointSucceeded = false;
    private volatile boolean isSynchronized = false;

    /**********************
     * encode, decode
     **********************/
    private EncoderFactory encoderFactory;
    DataElementFactory factory = new DataElementFactory() {
        public DataElement createElement(int index) {
            return encoderFactory.createHLAfloat32LE();
        }
    };

    /**********************
     * physical device time
     **********************/
    HLAfloat64Interval realTimeOffset = new HLAfloat64IntervalImpl(0.00);
    private HLAfloat64Time realTime = new HLAfloat64TimeImpl(0.00);
    public void setRealTimeOffset(String realTimeOffset) {
        this.realTimeOffset = new HLAfloat64IntervalImpl(Double.parseDouble(realTimeOffset)-currentTime.getValue());
    }
    public void setRealTime(String realTime) {
        this.realTime = new HLAfloat64TimeImpl(Double.parseDouble(realTime));
    }

    /**********************
     * Constructor
     **********************/
    private FederateAttributes federateAttributes;
    public Federate(FederateParameters federateParameters) {
        federateAttributes = new FederateAttributes();
        federateAttributes.setName(federateParameters.getFederateName());
        federateAttributes.setFederation(federateParameters.getFederationName());
        federateAttributes.setCrcAddress(federateParameters.getCrcAddress());

        if("Yes".equals(federateParameters.getIsPhysicalDevice())) {
            this.setIsPhysicalDevice(true);
        }

        federateAttributes.setType(federateParameters.getType());

        String[] tmp = federateParameters.getFomUrl().split("/");
        federateAttributes.setFomName(tmp[tmp.length - 1]);

        federateAttributes.setMechanism(federateParameters.getMechanism());
        federateAttributes.setFomUrl(federateParameters.getFomUrl());
        federateAttributes.setStrategy(federateParameters.getStrategy());
        federateAttributes.setStep(federateParameters.getStep());
        federateAttributes.setLookahead(federateParameters.getLookahead());
    }

    public FederateAttributes getFederateAttributes() {
        federateAttributes.setTime(currentTime.getValue());
        federateAttributes.setStatus(status);
        return federateAttributes;
    }

    private HLAfloat64Time currentTime;
    private HLAfloat64Interval advancedStep;
    public Double getTimeToMoveTo() {
        return currentTime.getValue();
    }

    public String createAndJoin() {
        try {
            /**********************
             * get Rti ambassador and connect with it.
             **********************/
            try {
                RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
                rtiAmbassador = rtiFactory.getRtiAmbassador();
                encoderFactory = rtiFactory.getEncoderFactory();
            } catch (Exception e) {
                return "Unable to create RTI ambassador.";
            }
            String crcAddress = federateAttributes.getCrcAddress();
            String settingsDesignator = "crcAddress=" + crcAddress;
            try {
                rtiAmbassador.connect(this, CallbackModel.HLA_IMMEDIATE, settingsDesignator);
            } catch (RTIexception e) {
                return "Wrong CRC Address";
            }

            /**********************
             * Clean up old federation
             **********************/
            try {
                rtiAmbassador.destroyFederationExecution(federateAttributes.getFederation());
            } catch (FederatesCurrentlyJoined | FederationExecutionDoesNotExist ignored) {
            }

            /**********************
             * Create federation
             **********************/
            //String s = "http://localhost:8080/assets/config/HLADemo.xml";
            URL url = new URL(federateAttributes.getFomUrl());
            try {
                rtiAmbassador.createFederationExecution(federateAttributes.getFederation(), new URL[]{url}, "HLAfloat64Time");
            } catch (FederationExecutionAlreadyExists ignored) {
            } catch (InconsistentFDD | ErrorReadingFDD | CouldNotOpenFDD e) {
                return "Wrong FOM File";
            }

            /**********************
             * Join current federate(specified with this) into current federation(specified with rtiAmbassador)
             **********************/
            try {
                rtiAmbassador.joinFederationExecution(federateAttributes.getName(), federateAttributes.getTypeName(), federateAttributes.getFederation(), new URL[]{url});
            } catch (FederateNameAlreadyInUse e) {
                return "Federate name has been already in use.";
            }
            /**********************
             * Add by Hypocrisy on 03/28/2015
             * Time Management Variables.
             **********************/
            HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl( Double.parseDouble(federateAttributes.getLookahead()) );
            if ("Regulating".equals(federateAttributes.getStrategy())) {
                rtiAmbassador.enableTimeRegulation(lookahead);
            } else if ("Constrained".equals(federateAttributes.getStrategy())) {
                rtiAmbassador.enableTimeConstrained();
            } else if("Regulating and Constrained".equals(federateAttributes.getStrategy())){
                rtiAmbassador.enableTimeRegulation(lookahead);
                rtiAmbassador.enableTimeConstrained();
            } else {
                //rtiAmbassador.disableTimeRegulation();   // If it is not enabled, this method will throw exception.
                //rtiAmbassador.disableTimeConstrained();
            }

            rtiAmbassador.enableCallbacks();
        } catch (Exception e) {
            return "Unable to join";
        }
        try {
            /**********************
             * Subscribe and publish objects
             **********************/
            subscribeAndPublishObjects();
            /**********************
             * Subscribe and publish interactions
             **********************/
            subscribeAndPublishInteractions();

            /**********************
             * Reserve object instance name and register object instance
             **********************/
            //name = federateAttributes.getName();
            do {
                Date date = new Date();
                name = "id" + Long.toString(date.getTime());
                try {
                    reservationComplete = false;
                    rtiAmbassador.reserveObjectInstanceName(name);
                    synchronized (reservationSemaphore) {
                        // Wait for response from RTI
                        while (!reservationComplete) {
                            try {
                                reservationSemaphore.wait();
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                    if (!reservationSucceeded) {
                        return "Name already taken, try again.";
                    }
                } catch (IllegalName e) {
                    return "Illegal name. Try again.";
                } catch (RTIexception e) {
                    return "RTI exception when reserving name: " + e.getMessage();
                }
            } while (!reservationSucceeded);
            objectInstanceId = rtiAmbassador.registerObjectInstance(objectClassHandles[federateAttributes.getType()], name);

            /**********************
             * Register Synchronization Point
             **********************/
            rtiAmbassador.enableAsynchronousDelivery();
            byte[] tag = {};
            try {
                rtiAmbassador.registerFederationSynchronizationPoint(SYNC_POINT, tag);
            } catch (Exception ignored) {

            }
            try {
                rtiAmbassador.synchronizationPointAchieved(SYNC_POINT);
            } catch (RTIexception ignored) {

            }

            // If there is constrained federates, and this federate is regulating, the start time of this federate is not 0.
            currentTime = (HLAfloat64Time) rtiAmbassador.queryLogicalTime();
            advancedStep = new HLAfloat64IntervalImpl( Double.parseDouble(federateAttributes.getStep()) );
        } catch (RTIexception e) {
            return "Fail to publish or subscribe: " + e.getMessage();
        }

        return "Success";
    }

    public void update(UpdateParameters updateParameters) {
        federateAttributes.setStrategy(updateParameters.getStrategy());
        federateAttributes.setMechanism(updateParameters.getMechanism());
        federateAttributes.setStep(updateParameters.getStep());
        federateAttributes.setLookahead(updateParameters.getLookahead());

        HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl( Double.parseDouble(updateParameters.getLookahead()) );
        try {
            if ("Regulating".equals(federateAttributes.getStrategy())) {
                if(isRegulating) {
                    rtiAmbassador.modifyLookahead(lookahead);
                } else {
                    rtiAmbassador.enableTimeRegulation(lookahead);
                }
                if(isConstrained) {
                    rtiAmbassador.disableTimeConstrained();
                    isConstrained = false;
                }
            } else if ("Constrained".equals(federateAttributes.getStrategy())) {
                if(!isConstrained) {
                    rtiAmbassador.enableTimeConstrained();
                }
                if(isRegulating) {
                    rtiAmbassador.disableTimeRegulation();
                    isRegulating = false;
                }
            } else if ("Regulating and Constrained".equals(federateAttributes.getStrategy())) {
                if(isRegulating) {
                    rtiAmbassador.modifyLookahead(lookahead);
                } else {
                    rtiAmbassador.enableTimeRegulation(lookahead);
                }
                if(!isConstrained) {
                    rtiAmbassador.enableTimeConstrained();
                }
            } else {
                if(isRegulating) {
                    rtiAmbassador.disableTimeRegulation();
                    isRegulating = false;
                }
                if(isConstrained) {
                    rtiAmbassador.disableTimeConstrained();
                    isConstrained = false;
                }
            }
        } catch (Exception ignored) {

        }
        advancedStep = new HLAfloat64IntervalImpl( Double.parseDouble(updateParameters.getStep()) );
    }

    public ParameterHandleValueMap setParameters() {
        // To be fixed
        try {
            ParameterHandleValueMap parameters = rtiAmbassador.getParameterHandleValueMapFactory().create(1);

            HLAinteger16LE statusEncoder = encoderFactory.createHLAinteger16LE();
            HLAunicodeString positionEncoder = encoderFactory.createHLAunicodeString();
            HLAfloat32LE index = encoderFactory.createHLAfloat32LE();
            HLAvariableArray variableEncoder = encoderFactory.createHLAvariableArray(factory);

            switch (federateAttributes.getType()) {
                case 0:
                    statusEncoder.setValue((short) 1);
                    parameters.put(cruiseMissileStatus, statusEncoder.toByteArray());
                    break;
                case 1:
                    statusEncoder.setValue((short) 1);
                    positionEncoder.setValue("Early Warning Radar");
                    parameters.put(early_warningRadarStatus, statusEncoder.toByteArray());
                    parameters.put(early_warningRadarPosition, positionEncoder.toByteArray());
                    break;
                case 2:
                    index.setValue((float) 1.00);
                    variableEncoder.addElement(index);
                    parameters.put(strategyOfMissionDistribution, variableEncoder.toByteArray());
                    break;
                case 3:
                    statusEncoder.setValue((short) 1);
                    parameters.put(anti_aircraftStatus, statusEncoder.toByteArray());
                    break;
                case 4:
                    index.setValue((float) 1.00);
                    variableEncoder.addElement(index);
                    parameters.put(route, variableEncoder.toByteArray());
                    break;
                case 5:
                    statusEncoder.setValue((short) 1);
                    positionEncoder.setValue("Tracking Radar for Enemy Target");
                    parameters.put(trackingRadarForEnemyStatus, statusEncoder.toByteArray());
                    parameters.put(trackingRadarForEnemyPosition, positionEncoder.toByteArray());
                    break;
                case 6:
                    statusEncoder.setValue((short) 1);
                    positionEncoder.setValue("Tracking Radar for Our Target");
                    parameters.put(trackingRadarForOurStatus, statusEncoder.toByteArray());
                    parameters.put(trackingRadarForOurPosition, positionEncoder.toByteArray());
                    break;
                default:
                    HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(name);
                    HLAunicodeString messageEncoder = encoderFactory.createHLAunicodeString();
                    String message = "Test";
                    messageEncoder.setValue(message);
                    parameters.put(communicationMessage, messageEncoder.toByteArray());
                    parameters.put(communicationSender, nameEncoder.toByteArray());
                    break;
            }
            return parameters;
        } catch (RTIexception ignored) {
        }
        return null;
    }

    public boolean isFirst = true;
    @Override
    public void run() {
        try {
            /**********************
             * Send interaction and update attributes for every 1 second.
             * Send interaction may be into failure because of time management,
             * but attributes update should success for every iteration for its independence of time management.
             **********************/
            Integer objectAttribute = 0;
            while(state) {
                // update object  attributes
                objectAttribute++;
                AttributeHandleValueMap attributeHandleValueMap = new AttributeHandleValueMapImpl();
                for (AttributeHandle attributeHandle : mapObjectAttributes.get(objectClassHandles[federateAttributes.getType()])) {
                    attributeHandleValueMap.put(attributeHandle, encoderFactory.createHLAunicodeString(objectAttribute.toString()).toByteArray());
                }
                rtiAmbassador.updateAttributeValues(objectInstanceId,attributeHandleValueMap,null);
                // end of object attributes update.

                // send interactions and request time advancement
                ParameterHandleValueMap parameters = setParameters();
                if(status && !isPhysicalDevice && federateAttributes.getMechanism() == 0) {
                    try {
                        rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                        rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()],parameters,null);
                    } catch (Exception ignored) {
                    }
                }

                if(status && !isPhysicalDevice && federateAttributes.getMechanism() == 1) {
                    HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl(Double.parseDouble(federateAttributes.getLookahead()));
                    HLAfloat64Time timestamp = currentTime.add(lookahead).add(advancedStep);
                    try {
                        rtiAmbassador.nextMessageRequest(currentTime.add(advancedStep));
                        rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()], parameters, null, timestamp);
                    } catch (Exception ignored) {
                        //e.printStackTrace();
                    }
                    /*
                    try {
                        //rtiAmbassador.changeInteractionOrderType(messageId,OrderType.TIMESTAMP);
                        rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()], parameters, null, timestamp);
                    } catch (RTIexception e) {
                        e.printStackTrace();
                    }
                    */
                }

                if(status && isPhysicalDevice && federateAttributes.getMechanism() == 0) {
                    try {
                        rtiAmbassador.timeAdvanceRequest(realTime.subtract(realTimeOffset));
                        rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()], parameters, null);
                    } catch (Exception ignored) {
                    }
                }

                if(status && isPhysicalDevice && federateAttributes.getMechanism() == 1) {
                    HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl(Double.parseDouble(federateAttributes.getLookahead()));
                    HLAfloat64Time timestamp = realTime.subtract(realTimeOffset).add(lookahead);
                    try {
                        rtiAmbassador.timeAdvanceRequest(realTime.subtract(realTimeOffset));
                        rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()], parameters, null, timestamp);
                    } catch (Exception ignored) {
                    }
                    /*
                    try {
                        //rtiAmbassador.changeInteractionOrderType(messageId,OrderType.TIMESTAMP);
                        rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()], parameters, null, timestamp);
                    } catch (RTIexception e) {
                    }
                    */
                }

                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        try {
            rtiAmbassador.resignFederationExecution(ResignAction.DELETE_OBJECTS_THEN_DIVEST);
            try {
                rtiAmbassador.destroyFederationExecution(federateAttributes.getFederation());
            } catch (FederatesCurrentlyJoined ignored) {
            }
            rtiAmbassador.disconnect();
            rtiAmbassador = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**********************
     * Callbacks extends from NUllFederateAmbassador Class.
     **********************/
    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] userSuppliedTag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        System.out.println(this.federateAttributes.getName() + " received interactions");

        HLAinteger16LE statusDecoder = encoderFactory.createHLAinteger16LE();
        HLAunicodeString positionDecoder = encoderFactory.createHLAunicodeString();
        HLAvariableArray variableArrayDecoder = encoderFactory.createHLAvariableArray(factory);

        List<ParameterHandle> parameters = mapInteractionParameters.get(interactionClass);
        for (ParameterHandle parameterHandle : parameters) {
            try {
                if (parameterHandle.equals(cruiseMissileStatus)
                        || parameterHandle.equals(early_warningRadarStatus)
                        || parameterHandle.equals(anti_aircraftStatus)
                        || parameterHandle.equals(trackingRadarForEnemyStatus)
                        || parameterHandle.equals(trackingRadarForOurStatus)
                        ) {
                    statusDecoder.decode(theParameters.get(parameterHandle));
                    System.out.print("Status: " + statusDecoder.getValue() + ";");
                }
                if (parameterHandle.equals(early_warningRadarPosition)
                        || parameterHandle.equals(trackingRadarForEnemyPosition)
                        || parameterHandle.equals(trackingRadarForOurPosition)
                        ) {
                    positionDecoder.decode(theParameters.get(parameterHandle));
                    System.out.println("Position: " + positionDecoder.getValue());
                }
                if (parameterHandle.equals(strategyOfMissionDistribution)
                        || parameterHandle.equals(route)
                        ) {
                    variableArrayDecoder.decode(theParameters.get(parameterHandle));
                    String variableArray = variableArrayDecoder.toString();
                    System.out.println("Distribution: " + variableArray);
                }

                // Test
                if (parameterHandle.equals(communicationMessage)
                        || parameterHandle.equals(communicationSender)
                        ) {
                    HLAunicodeString communicationDecoder = encoderFactory.createHLAunicodeString();
                    communicationDecoder.decode(theParameters.get(parameterHandle));
                    System.out.println("Communication: " + communicationDecoder.getValue());
                }
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] userSuppliedTag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   LogicalTime theTime,
                                   OrderType receivedOrdering,
                                   SupplementalReceiveInfo receiveInfo) {
        System.out.println(this.federateAttributes.getName() + " received interaction with time " + theTime.toString());

        try {
            advancedStep = (HLAfloat64Interval) theTime.distance(currentTime);
        } catch (InvalidLogicalTime invalidLogicalTime) {
            invalidLogicalTime.printStackTrace();
        }

        HLAinteger16LE statusDecoder = encoderFactory.createHLAinteger16LE();
        HLAunicodeString positionDecoder = encoderFactory.createHLAunicodeString();
        HLAvariableArray variableArrayDecoder = encoderFactory.createHLAvariableArray(factory);

        List<ParameterHandle> parameters = mapInteractionParameters.get(interactionClass);
        for (ParameterHandle parameterHandle : parameters) {
            try {
                if (parameterHandle.equals(cruiseMissileStatus)
                        || parameterHandle.equals(early_warningRadarStatus)
                        || parameterHandle.equals(anti_aircraftStatus)
                        || parameterHandle.equals(trackingRadarForEnemyStatus)
                        || parameterHandle.equals(trackingRadarForOurStatus)
                        ) {
                    statusDecoder.decode(theParameters.get(parameterHandle));
                    System.out.print("Status: " + statusDecoder.getValue() + ";");
                }
                if (parameterHandle.equals(early_warningRadarPosition)
                        || parameterHandle.equals(trackingRadarForEnemyPosition)
                        || parameterHandle.equals(trackingRadarForOurPosition)
                        ) {
                    positionDecoder.decode(theParameters.get(parameterHandle));
                    System.out.println("Position: " + positionDecoder.getValue());
                }
                if (parameterHandle.equals(strategyOfMissionDistribution)
                        || parameterHandle.equals(route)
                        ) {
                    variableArrayDecoder.decode(theParameters.get(parameterHandle));
                    String variableArray = variableArrayDecoder.toString();
                    System.out.println("Distribution or Route: " + variableArray);
                }

                // Test
                if (parameterHandle.equals(communicationMessage)
                        || parameterHandle.equals(communicationSender)
                        ) {
                    HLAunicodeString communicationDecoder = encoderFactory.createHLAunicodeString();
                    communicationDecoder.decode(theParameters.get(parameterHandle));
                    System.out.println("Communication: " + communicationDecoder.getValue());
                }
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] userSuppliedTag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   LogicalTime theTime,
                                   OrderType receivedOrdering,
                                   MessageRetractionHandle retractionHandle,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        System.out.println(this.federateAttributes.getName() + " received interaction with time " + theTime.toString());

        try {
            advancedStep = (HLAfloat64Interval) theTime.distance(currentTime);
        } catch (InvalidLogicalTime invalidLogicalTime) {
            invalidLogicalTime.printStackTrace();
        }

        HLAinteger16LE statusDecoder = encoderFactory.createHLAinteger16LE();
        HLAunicodeString positionDecoder = encoderFactory.createHLAunicodeString();
        HLAvariableArray variableArrayDecoder = encoderFactory.createHLAvariableArray(factory);

        List<ParameterHandle> parameters = mapInteractionParameters.get(interactionClass);
        for (ParameterHandle parameterHandle : parameters) {
            try {
                if (parameterHandle.equals(cruiseMissileStatus)
                        || parameterHandle.equals(early_warningRadarStatus)
                        || parameterHandle.equals(anti_aircraftStatus)
                        || parameterHandle.equals(trackingRadarForEnemyStatus)
                        || parameterHandle.equals(trackingRadarForOurStatus)
                        ) {
                    statusDecoder.decode(theParameters.get(parameterHandle));
                    System.out.print("Status: " + statusDecoder.getValue() + ";");
                }
                if (parameterHandle.equals(early_warningRadarPosition)
                        || parameterHandle.equals(trackingRadarForEnemyPosition)
                        || parameterHandle.equals(trackingRadarForOurPosition)
                        ) {
                    positionDecoder.decode(theParameters.get(parameterHandle));
                    System.out.println("Position: " + positionDecoder.getValue());
                }
                if (parameterHandle.equals(strategyOfMissionDistribution)
                        || parameterHandle.equals(route)
                        ) {
                    variableArrayDecoder.decode(theParameters.get(parameterHandle));
                    String variableArray = variableArrayDecoder.toString();
                    System.out.println("Distribution: " + variableArray);
                }

                // Test
                if (parameterHandle.equals(communicationMessage)
                        || parameterHandle.equals(communicationSender)
                        ) {
                    HLAunicodeString communicationDecoder = encoderFactory.createHLAunicodeString();
                    communicationDecoder.decode(theParameters.get(parameterHandle));
                    System.out.println("Communication: " + communicationDecoder.getValue());
                }
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }
    }

    @Override
    public final void objectInstanceNameReservationSucceeded(String objectName) {
        synchronized (reservationSemaphore) {
            reservationComplete = true;
            reservationSucceeded = true;
            reservationSemaphore.notifyAll();
        }
    }

    @Override
    public final void objectInstanceNameReservationFailed(String objectName) {
        synchronized (reservationSemaphore) {
            reservationComplete = true;
            reservationSucceeded = false;
            reservationSemaphore.notifyAll();
        }
    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] userSuppliedTag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo) {
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] userSuppliedTag,
                                       OrderType sentOrdering,
                                       TransportationTypeHandle theTransport,
                                       SupplementalReflectInfo reflectInfo) {
        try {
            HLAunicodeString h = encoderFactory.createHLAunicodeString();
            ObjectClassHandle o = rtiAmbassador.getKnownObjectClassHandle(theObject);
            List<AttributeHandle> list = mapObjectAttributes.get(o);
            for(AttributeHandle ah : list) {
                h.decode(theAttributes.get(ah));
                System.out.println(h.toString());
            }
        } catch (Exception e) {
            System.out.println("Object Instance Not Known");
        }
    }

    @Override
    public final void provideAttributeValueUpdate(ObjectInstanceHandle theObject,
                                                  AttributeHandleSet theAttributes,
                                                  byte[] userSuppliedTag) {
        System.out.println("Prepare to update attribute");
    }

    @Override
    public void timeRegulationEnabled(LogicalTime logicalTime) throws FederateInternalError {
        isRegulating = true;
        System.out.println("Federate is regulating.");
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime logicalTime) throws FederateInternalError {
        isConstrained = true;
        System.out.println("Federate is constrained.");
    }

    @Override
    public void synchronizationPointRegistrationSucceeded(String synchronizationPointLabel) throws FederateInternalError {
        isRegisterSyncPointSucceeded = true;
        System.out.println("Register Synchronized Point Successfully");
    }

    @Override
    public void synchronizationPointRegistrationFailed(String synchronizationPointLabel, SynchronizationPointFailureReason reason)
            throws FederateInternalError {
        if(reason == SynchronizationPointFailureReason.SYNCHRONIZATION_POINT_LABEL_NOT_UNIQUE) {
            isRegisterSyncPointSucceeded = true;
            System.out.println("Have Registered Synchronized Point");
        }
    }

    @Override
    public void announceSynchronizationPoint(String synchronizationPointLabel, byte[] userSuppliedTag) throws FederateInternalError {
        System.out.println("Announce Synchronized Point Successfully");
    }


    @Override
    public void federationSynchronized(String synchronizationPointLabel, FederateHandleSet failedToSyncSet) throws FederateInternalError {
        isSynchronized = true;
        System.out.println("Achieve Synchronized Point Successfully");
    }

    @Override
    public void timeAdvanceGrant(LogicalTime logicalTime) throws FederateInternalError {
        currentTime = (HLAfloat64Time) logicalTime;
        System.out.println(this.federateAttributes.getName() + ": Time Advance to " + logicalTime.toString() + " Successfully");
    }

    /**********************
     * Subscribe and publish objects
     **********************/
    private void subscribeAndPublishObjects() {
        List<AttributeHandle> tmp;
        try {
            ObjectClassHandle objectCruiseMissile = rtiAmbassador.getObjectClassHandle("CruiseMissile");
            AttributeHandle cruiseMissileAttributePosition = rtiAmbassador.getAttributeHandle(objectCruiseMissile, "Position");
            tmp = new ArrayList<>();
            tmp.add(cruiseMissileAttributePosition);
            mapObjectAttributes.put(objectCruiseMissile,tmp);

            ObjectClassHandle objectEarly_warningRadar = rtiAmbassador.getObjectClassHandle("Early_warningRadar");
            AttributeHandle early_warningRadarAttributeId = rtiAmbassador.getAttributeHandle(objectEarly_warningRadar, "ID");
            tmp = new ArrayList<>();
            tmp.add(early_warningRadarAttributeId);
            mapObjectAttributes.put(objectEarly_warningRadar,tmp);

            ObjectClassHandle objectMissionDistribution = rtiAmbassador.getObjectClassHandle("MissionDistribution");
            AttributeHandle missionDistributionAttributeId = rtiAmbassador.getAttributeHandle(objectMissionDistribution, "ID");
            tmp = new ArrayList<>();
            tmp.add(missionDistributionAttributeId);
            mapObjectAttributes.put(objectMissionDistribution, tmp);

            ObjectClassHandle objectAnti_aircraftMissile = rtiAmbassador.getObjectClassHandle("Anti_aircraftMissile");
            AttributeHandle anti_aircraftMissileAttributePosition = rtiAmbassador.getAttributeHandle(objectAnti_aircraftMissile, "Position");
            tmp = new ArrayList<>();
            tmp.add(anti_aircraftMissileAttributePosition);
            mapObjectAttributes.put(objectAnti_aircraftMissile,tmp);

            ObjectClassHandle objectRoutePlanning = rtiAmbassador.getObjectClassHandle("RoutePlanning");
            AttributeHandle routePlanningAttributeId = rtiAmbassador.getAttributeHandle(objectRoutePlanning, "ID");
            tmp = new ArrayList<>();
            tmp.add(routePlanningAttributeId);
            mapObjectAttributes.put(objectRoutePlanning,tmp);

            ObjectClassHandle objectTrackingRadarForEnemy = rtiAmbassador.getObjectClassHandle("TrackingRadarForEnemyTarget");
            AttributeHandle trackingRadarForEnemyAttributeId = rtiAmbassador.getAttributeHandle(objectTrackingRadarForEnemy, "ID");
            tmp = new ArrayList<>();
            tmp.add(trackingRadarForEnemyAttributeId);
            mapObjectAttributes.put(objectTrackingRadarForEnemy,tmp);

            ObjectClassHandle objectTrackingRadarForOur = rtiAmbassador.getObjectClassHandle("TrackingRadarForOurTarget");
            AttributeHandle trackingRadarForOurAttributeId = rtiAmbassador.getAttributeHandle(objectTrackingRadarForOur, "ID");
            tmp = new ArrayList<>();
            tmp.add(trackingRadarForOurAttributeId);
            mapObjectAttributes.put(objectTrackingRadarForOur,tmp);

            ObjectClassHandle objectParticipant = rtiAmbassador.getObjectClassHandle("Participant");
            AttributeHandle participantAttributeId = rtiAmbassador.getAttributeHandle(objectParticipant, "Name");
            tmp = new ArrayList<>();
            tmp.add(participantAttributeId);
            mapObjectAttributes.put(objectParticipant,tmp);

            objectClassHandles = new ObjectClassHandle[]{objectCruiseMissile, objectEarly_warningRadar, objectMissionDistribution, objectAnti_aircraftMissile, objectRoutePlanning, objectTrackingRadarForEnemy, objectTrackingRadarForOur, objectParticipant};

            AttributeHandleSet publishAttributeHandleSet = rtiAmbassador.getAttributeHandleSetFactory().create();
            AttributeHandleSet subscribeAttributeHandleSet = rtiAmbassador.getAttributeHandleSetFactory().create();

            // Publish object attributes.
            for (AttributeHandle attributeHandle : mapObjectAttributes.get(objectClassHandles[federateAttributes.getType()])) {
                publishAttributeHandleSet.add(attributeHandle);
            }
            rtiAmbassador.publishObjectClassAttributes(objectClassHandles[federateAttributes.getType()],publishAttributeHandleSet);

            // Subscribe object attributes.
            switch (federateAttributes.getType()) {
                case 0:
                    break;
                case 1:
                    subscribeAttributeHandleSet.add(cruiseMissileAttributePosition);
                    rtiAmbassador.subscribeObjectClassAttributes(objectCruiseMissile, subscribeAttributeHandleSet);
                    break;
                case 2:
                    break;
                case 3:
                    break;
                case 4:
                    break;
                case 5:
                    subscribeAttributeHandleSet.add(cruiseMissileAttributePosition);
                    rtiAmbassador.subscribeObjectClassAttributes(objectCruiseMissile, subscribeAttributeHandleSet);
                    break;
                case 6:
                    subscribeAttributeHandleSet.add(anti_aircraftMissileAttributePosition);
                    rtiAmbassador.subscribeObjectClassAttributes(objectAnti_aircraftMissile, subscribeAttributeHandleSet);
                    break;
                default:
                    subscribeAttributeHandleSet = publishAttributeHandleSet;
                    rtiAmbassador.publishObjectClassAttributes(objectParticipant, subscribeAttributeHandleSet);
                    break;
            }
        } catch (RTIexception ignored) {
        }
    }

    /**********************
     * Subscribe and publish interactions
     **********************/
    private void subscribeAndPublishInteractions() {
        List<ParameterHandle> tmp;

        try {
            InteractionClassHandle cruiseMissile = rtiAmbassador.getInteractionClassHandle("CruiseMissile");
            cruiseMissileStatus = rtiAmbassador.getParameterHandle(cruiseMissile,"Status");
            tmp = new ArrayList<>();
            tmp.add(cruiseMissileStatus);
            mapInteractionParameters.put(cruiseMissile,tmp);

            InteractionClassHandle early_warningRadar = rtiAmbassador.getInteractionClassHandle("Early_warningRadar");
            early_warningRadarStatus = rtiAmbassador.getParameterHandle(early_warningRadar,"Status");
            early_warningRadarPosition = rtiAmbassador.getParameterHandle(early_warningRadar,"Position");
            tmp = new ArrayList<>();
            tmp.add(early_warningRadarStatus);
            tmp.add(early_warningRadarPosition);
            mapInteractionParameters.put(early_warningRadar,tmp);

            InteractionClassHandle missionDistribution = rtiAmbassador.getInteractionClassHandle("MissionDistribution");
            strategyOfMissionDistribution = rtiAmbassador.getParameterHandle(missionDistribution,"Strategy");
            tmp = new ArrayList<>();
            tmp.add(strategyOfMissionDistribution);
            mapInteractionParameters.put(missionDistribution,tmp);

            InteractionClassHandle anti_aircraftMissile = rtiAmbassador.getInteractionClassHandle("Anti_aircraftMissile");
            anti_aircraftStatus = rtiAmbassador.getParameterHandle(anti_aircraftMissile,"Status");
            tmp = new ArrayList<>();
            tmp.add(anti_aircraftStatus);
            mapInteractionParameters.put(anti_aircraftMissile,tmp);

            InteractionClassHandle routePlanning = rtiAmbassador.getInteractionClassHandle("RoutePlanning");
            route = rtiAmbassador.getParameterHandle(routePlanning,"Route");
            tmp = new ArrayList<>();
            tmp.add(route);
            mapInteractionParameters.put(routePlanning,tmp);

            InteractionClassHandle trackingRadarForEnemy = rtiAmbassador.getInteractionClassHandle("TrackingRadarForEnemyTarget");
            trackingRadarForEnemyStatus = rtiAmbassador.getParameterHandle(trackingRadarForEnemy,"Status");
            trackingRadarForEnemyPosition = rtiAmbassador.getParameterHandle(trackingRadarForEnemy,"Position");
            tmp = new ArrayList<>();
            tmp.add(trackingRadarForEnemyStatus);
            tmp.add(trackingRadarForEnemyPosition);
            mapInteractionParameters.put(trackingRadarForEnemy,tmp);

            InteractionClassHandle trackingRadarForOur = rtiAmbassador.getInteractionClassHandle("TrackingRadarForOurTarget");
            trackingRadarForOurStatus = rtiAmbassador.getParameterHandle(trackingRadarForOur,"Status");
            trackingRadarForOurPosition = rtiAmbassador.getParameterHandle(trackingRadarForOur,"Position");
            tmp = new ArrayList<>();
            tmp.add(trackingRadarForOurStatus);
            tmp.add(trackingRadarForOurPosition);
            mapInteractionParameters.put(trackingRadarForOur,tmp);

            InteractionClassHandle communication = rtiAmbassador.getInteractionClassHandle("Communication");
            communicationMessage = rtiAmbassador.getParameterHandle(communication, "Message");
            communicationSender = rtiAmbassador.getParameterHandle(communication, "Sender");
            tmp = new ArrayList<>();
            tmp.add(communicationMessage);
            tmp.add(communicationSender);
            mapInteractionParameters.put(communication,tmp);

            interactionClasses = new InteractionClassHandle[]{cruiseMissile,early_warningRadar,missionDistribution,anti_aircraftMissile,routePlanning,trackingRadarForEnemy, trackingRadarForOur, communication};

            // Publish interactions
            rtiAmbassador.publishInteractionClass(interactionClasses[federateAttributes.getType()]);
            // Subscribe interactions
            switch (federateAttributes.getType()) {
                case 0:
                    break;
                case 1:
                    //rtiAmbassador.subscribeInteractionClass(cruiseMissile);
                    break;
                case 2:
                    rtiAmbassador.subscribeInteractionClass(early_warningRadar);
                    rtiAmbassador.subscribeInteractionClass(trackingRadarForOur);
                    break;
                case 3:
                    rtiAmbassador.subscribeInteractionClass(routePlanning);
                    break;
                case 4:
                    rtiAmbassador.subscribeInteractionClass(trackingRadarForEnemy);
                    rtiAmbassador.subscribeInteractionClass(trackingRadarForOur);
                    rtiAmbassador.subscribeInteractionClass(missionDistribution);
                    break;
                case 5:
                    break;
                case 6:
                    break;
                default:
                    rtiAmbassador.subscribeInteractionClass(communication);
                    break;
            }
        } catch (RTIexception ignored) {
        }
    }
}
