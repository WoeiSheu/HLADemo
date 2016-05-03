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
    private ParameterHandle cruiseMissilePosition;
    private ParameterHandle early_warningRadarStatus;
    private ParameterHandle early_warningRadarPosition;
    private ParameterHandle strategyOfMissionDistribution;
    private ParameterHandle anti_aircraftStatus;
    private ParameterHandle anti_aircraftMissilePosition;
    private ParameterHandle route;
    private ParameterHandle trackingRadarStatus;
    private ParameterHandle trackingRadarPosition;
    private ParameterHandle communicationMessage;
    private ParameterHandle communicationSender;

    private InteractionClassHandle[] interactionClasses;
    private Map<InteractionClassHandle,List<ParameterHandle>> mapInteractionParameters = new HashMap<>();
    /**********************
     * All object instance handle and their attributes' handle
     **********************/
    private ObjectClassHandle[] objectClassHandles;
    private Map<ObjectClassHandle,List<AttributeHandle>> mapObjectAttributes = new HashMap<>();
    private ObjectInstanceHandle userId;

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
            Date date = new Date();
            name = "id" + Long.toString(date.getTime());
            do {
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
            userId = rtiAmbassador.registerObjectInstance(objectClassHandles[federateAttributes.getType()], name);
            AttributeHandleValueMap attributeHandleValueMap = new AttributeHandleValueMapImpl();
            for (AttributeHandle attributeHandle : mapObjectAttributes.get(objectClassHandles[federateAttributes.getType()])) {
                attributeHandleValueMap.put(attributeHandle, encoderFactory.createHLAunicodeString("0").toByteArray());
            }
            rtiAmbassador.updateAttributeValues(userId,attributeHandleValueMap,null);

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

            HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(name);
            HLAinteger16LE statusEncoder = encoderFactory.createHLAinteger16LE();
            HLAunicodeString messageEncoder = encoderFactory.createHLAunicodeString();
            HLAfloat32LE index = encoderFactory.createHLAfloat32LE();
            HLAvariableArray strategyEncoder = encoderFactory.createHLAvariableArray(factory);

            switch (federateAttributes.getType()) {
                case 0:
                    statusEncoder.setValue((short) 1);
                    messageEncoder.setValue("Cruise Missile Position");
                    parameters.put(cruiseMissileStatus, statusEncoder.toByteArray());
                    parameters.put(cruiseMissilePosition, messageEncoder.toByteArray());
                    break;
                case 1:
                    statusEncoder.setValue((short) 1);
                    messageEncoder.setValue("Early Warning Radar");
                    parameters.put(early_warningRadarStatus, statusEncoder.toByteArray());
                    parameters.put(early_warningRadarPosition, messageEncoder.toByteArray());
                    break;
                case 2:
                    index.setValue((float) 1.00);
                    strategyEncoder.addElement(index);
                    parameters.put(strategyOfMissionDistribution, strategyEncoder.toByteArray());
                    break;
                case 3:
                    statusEncoder.setValue((short) 1);
                    messageEncoder.setValue("Anti Aircraft Missile");
                    parameters.put(anti_aircraftStatus, statusEncoder.toByteArray());
                    parameters.put(anti_aircraftMissilePosition, messageEncoder.toByteArray());
                    break;
                case 4:
                    index.setValue((float) 1.00);
                    strategyEncoder.addElement(index);
                    parameters.put(route, strategyEncoder.toByteArray());
                    break;
                case 5:
                    statusEncoder.setValue((short) 1);
                    messageEncoder.setValue("Tracking Radar");
                    parameters.put(trackingRadarStatus, statusEncoder.toByteArray());
                    parameters.put(trackingRadarPosition, messageEncoder.toByteArray());
                    break;
                default:
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
            Integer i = 0;
            while(state) {
                // update object  attributes
                i++;
                AttributeHandleValueMap attributeHandleValueMap = new AttributeHandleValueMapImpl();
                for (AttributeHandle attributeHandle : mapObjectAttributes.get(objectClassHandles[federateAttributes.getType()])) {
                    attributeHandleValueMap.put(attributeHandle, encoderFactory.createHLAunicodeString(i.toString()).toByteArray());
                }
                rtiAmbassador.updateAttributeValues(userId,attributeHandleValueMap,null);
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
                    } catch (Exception e) {
                        e.printStackTrace();
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
                    } catch (Exception e) {
                    }
                }

                if(status && isPhysicalDevice && federateAttributes.getMechanism() == 1) {
                    HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl(Double.parseDouble(federateAttributes.getLookahead()));
                    HLAfloat64Time timestamp = realTime.subtract(realTimeOffset).add(lookahead);
                    try {
                        rtiAmbassador.timeAdvanceRequest(realTime.subtract(realTimeOffset));
                        rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()], parameters, null, timestamp);
                    } catch (Exception e) {
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
        System.out.println(this.federateAttributes.getName());

        HLAinteger16LE statusDecoder = encoderFactory.createHLAinteger16LE();
        HLAunicodeString positionDecoder = encoderFactory.createHLAunicodeString();
        HLAvariableArray variableArrayDecoder = encoderFactory.createHLAvariableArray(factory);

        if(interactionClass.equals(interactionClasses[0])) {
            if (!theParameters.containsKey(cruiseMissileStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(cruiseMissilePosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(cruiseMissileStatus));
                positionDecoder.decode(theParameters.get(cruiseMissilePosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[1])) {
            if (!theParameters.containsKey(early_warningRadarStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(early_warningRadarPosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(early_warningRadarStatus));
                positionDecoder.decode(theParameters.get(early_warningRadarPosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[2])) {
            if (!theParameters.containsKey(strategyOfMissionDistribution)) {
                System.out.println("Bad message received: No Strategy.");
                return;
            }
            try {
                variableArrayDecoder.decode(theParameters.get(strategyOfMissionDistribution));
                String variableArray = variableArrayDecoder.toString();
                System.out.println( variableArray );
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[3])) {
            if (!theParameters.containsKey(anti_aircraftStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(anti_aircraftMissilePosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(anti_aircraftStatus));
                positionDecoder.decode(theParameters.get(anti_aircraftMissilePosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[4])) {
            if (!theParameters.containsKey(route)) {
                System.out.println("Bad message received: No Route.");
                return;
            }
            try {
                variableArrayDecoder.decode(theParameters.get(route));
                String variableArray = variableArrayDecoder.toString();

                System.out.println(variableArray);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[5])) {
            if (!theParameters.containsKey(trackingRadarStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(trackingRadarPosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(trackingRadarStatus));
                positionDecoder.decode(theParameters.get(trackingRadarPosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if (interactionClass.equals(interactionClasses[6])) {
            if (!theParameters.containsKey(communicationMessage)) {
                System.out.println("Bad message received: No text.");
                return;
            }
            if (!theParameters.containsKey(communicationSender)) {
                System.out.println("Bad message received: No sender.");
                return;
            }
            try {
                HLAunicodeString messageDecoder = encoderFactory.createHLAunicodeString();
                HLAunicodeString senderDecoder = encoderFactory.createHLAunicodeString();
                messageDecoder.decode(theParameters.get(communicationMessage));
                senderDecoder.decode(theParameters.get(communicationSender));
                String message = messageDecoder.getValue();
                String sender = senderDecoder.getValue();

                System.out.println(sender + ": " + message);
                System.out.print("> ");
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
        System.out.println("MiaoMiao");
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

        System.out.println(this.federateAttributes.getName() + ": " + theTime.toString());

        HLAinteger16LE statusDecoder = encoderFactory.createHLAinteger16LE();
        HLAunicodeString positionDecoder = encoderFactory.createHLAunicodeString();
        HLAvariableArray variableArrayDecoder = encoderFactory.createHLAvariableArray(factory);

        if(interactionClass.equals(interactionClasses[0])) {
            if (!theParameters.containsKey(cruiseMissileStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(cruiseMissilePosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(cruiseMissileStatus));
                positionDecoder.decode(theParameters.get(cruiseMissilePosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[1])) {
            if (!theParameters.containsKey(early_warningRadarStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(early_warningRadarPosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(early_warningRadarStatus));
                positionDecoder.decode(theParameters.get(early_warningRadarPosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[2])) {
            if (!theParameters.containsKey(strategyOfMissionDistribution)) {
                System.out.println("Bad message received: No Strategy.");
                return;
            }
            try {
                variableArrayDecoder.decode(theParameters.get(strategyOfMissionDistribution));
                String variableArray = variableArrayDecoder.toString();
                System.out.println( variableArray );
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[3])) {
            if (!theParameters.containsKey(anti_aircraftStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(anti_aircraftMissilePosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(anti_aircraftStatus));
                positionDecoder.decode(theParameters.get(anti_aircraftMissilePosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[4])) {
            if (!theParameters.containsKey(route)) {
                System.out.println("Bad message received: No Route.");
                return;
            }
            try {
                variableArrayDecoder.decode(theParameters.get(route));
                String variableArray = variableArrayDecoder.toString();

                System.out.println(variableArray);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if(interactionClass.equals(interactionClasses[5])) {
            if (!theParameters.containsKey(trackingRadarStatus)) {
                System.out.println("Bad message received: No Status.");
                return;
            }
            if (!theParameters.containsKey(trackingRadarPosition)) {
                System.out.println("Bad message received: No Position.");
                return;
            }
            try {
                statusDecoder.decode(theParameters.get(trackingRadarStatus));
                positionDecoder.decode(theParameters.get(trackingRadarPosition));
                int status = statusDecoder.getValue();
                String position = positionDecoder.getValue();

                System.out.println(status + " " + position);
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }

        if (interactionClass.equals(interactionClasses[6])) {
            if (!theParameters.containsKey(communicationMessage)) {
                System.out.println("Bad message received: No text.");
                return;
            }
            if (!theParameters.containsKey(communicationSender)) {
                System.out.println("Bad message received: No sender.");
                return;
            }
            try {
                HLAunicodeString messageDecoder = encoderFactory.createHLAunicodeString();
                HLAunicodeString senderDecoder = encoderFactory.createHLAunicodeString();
                messageDecoder.decode(theParameters.get(communicationMessage));
                senderDecoder.decode(theParameters.get(communicationSender));
                String message = messageDecoder.getValue();
                String sender = senderDecoder.getValue();

                System.out.println(sender + ": " + message);
                System.out.print("> ");
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
     * Extract functions for understanding easier.
     **********************/

    /**********************
     * Subscribe and publish objects
     **********************/
    private void subscribeAndPublishObjects() {
        List<AttributeHandle> tmp;
        try {
            ObjectClassHandle objectCruiseMissile = rtiAmbassador.getObjectClassHandle("CruiseMissile");
            AttributeHandle cruiseMissileAttributeId = rtiAmbassador.getAttributeHandle(objectCruiseMissile, "ID");
            tmp = new ArrayList<>();
            tmp.add(cruiseMissileAttributeId);
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
            AttributeHandle anti_aircraftMissileAttributeId = rtiAmbassador.getAttributeHandle(objectAnti_aircraftMissile, "ID");
            tmp = new ArrayList<>();
            tmp.add(anti_aircraftMissileAttributeId);
            mapObjectAttributes.put(objectAnti_aircraftMissile,tmp);

            ObjectClassHandle objectRoutePlanning = rtiAmbassador.getObjectClassHandle("RoutePlanning");
            AttributeHandle routePlanningAttributeId = rtiAmbassador.getAttributeHandle(objectRoutePlanning, "ID");
            tmp = new ArrayList<>();
            tmp.add(routePlanningAttributeId);
            mapObjectAttributes.put(objectRoutePlanning,tmp);

            ObjectClassHandle objectTrackingRadar = rtiAmbassador.getObjectClassHandle("TrackingRadar");
            AttributeHandle trackingRadarAttributeId = rtiAmbassador.getAttributeHandle(objectTrackingRadar, "ID");
            tmp = new ArrayList<>();
            tmp.add(trackingRadarAttributeId);
            mapObjectAttributes.put(objectTrackingRadar,tmp);

            ObjectClassHandle objectParticipant = rtiAmbassador.getObjectClassHandle("Participant");
            AttributeHandle participantAttributeId = rtiAmbassador.getAttributeHandle(objectParticipant, "Name");
            tmp = new ArrayList<>();
            tmp.add(participantAttributeId);
            mapObjectAttributes.put(objectParticipant,tmp);

            objectClassHandles = new ObjectClassHandle[]{objectCruiseMissile, objectEarly_warningRadar, objectMissionDistribution, objectAnti_aircraftMissile, objectRoutePlanning, objectTrackingRadar, objectParticipant};

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
                    subscribeAttributeHandleSet.add(cruiseMissileAttributeId);
                    rtiAmbassador.subscribeObjectClassAttributes(objectCruiseMissile, subscribeAttributeHandleSet);
                    break;
                case 2:
                    subscribeAttributeHandleSet.add(early_warningRadarAttributeId);
                    rtiAmbassador.subscribeObjectClassAttributes(objectEarly_warningRadar, subscribeAttributeHandleSet);
                    break;
                case 3:
                    subscribeAttributeHandleSet.add(routePlanningAttributeId);
                    rtiAmbassador.subscribeObjectClassAttributes(objectRoutePlanning, subscribeAttributeHandleSet);
                    break;
                case 4:
                    subscribeAttributeHandleSet.add(trackingRadarAttributeId);
                    rtiAmbassador.subscribeObjectClassAttributes(objectTrackingRadar, subscribeAttributeHandleSet);
                    break;
                case 5:
                    subscribeAttributeHandleSet.add(anti_aircraftMissileAttributeId);
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
            cruiseMissilePosition = rtiAmbassador.getParameterHandle(cruiseMissile,"Position");
            tmp = new ArrayList<>();
            tmp.add(cruiseMissileStatus);
            tmp.add(cruiseMissilePosition);
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
            anti_aircraftMissilePosition = rtiAmbassador.getParameterHandle(anti_aircraftMissile,"Position");
            tmp = new ArrayList<>();
            tmp.add(anti_aircraftStatus);
            tmp.add(anti_aircraftMissilePosition);
            mapInteractionParameters.put(anti_aircraftMissile,tmp);

            InteractionClassHandle routePlanning = rtiAmbassador.getInteractionClassHandle("RoutePlanning");
            route = rtiAmbassador.getParameterHandle(routePlanning,"Route");
            tmp = new ArrayList<>();
            tmp.add(route);
            mapInteractionParameters.put(routePlanning,tmp);

            InteractionClassHandle trackingRadar = rtiAmbassador.getInteractionClassHandle("TrackingRadar");
            trackingRadarStatus = rtiAmbassador.getParameterHandle(trackingRadar,"Status");
            trackingRadarPosition = rtiAmbassador.getParameterHandle(trackingRadar,"Position");
            tmp = new ArrayList<>();
            tmp.add(trackingRadarStatus);
            tmp.add(trackingRadarPosition);
            mapInteractionParameters.put(trackingRadar,tmp);

            InteractionClassHandle communication = rtiAmbassador.getInteractionClassHandle("Communication");
            communicationMessage = rtiAmbassador.getParameterHandle(communication, "Message");
            communicationSender = rtiAmbassador.getParameterHandle(communication, "Sender");
            tmp = new ArrayList<>();
            tmp.add(communicationMessage);
            tmp.add(communicationSender);
            mapInteractionParameters.put(communication,tmp);

            interactionClasses = new InteractionClassHandle[]{cruiseMissile,early_warningRadar,missionDistribution,anti_aircraftMissile,routePlanning,trackingRadar,communication};

            // Publish interactions
            rtiAmbassador.publishInteractionClass(interactionClasses[federateAttributes.getType()]);
            // Subscribe interactions
            switch (federateAttributes.getType()) {
                case 0:
                    break;
                case 1:
                    rtiAmbassador.subscribeInteractionClass(cruiseMissile);
                    break;
                case 2:
                    rtiAmbassador.subscribeInteractionClass(early_warningRadar);
                    rtiAmbassador.subscribeInteractionClass(trackingRadar);
                    break;
                case 3:
                    rtiAmbassador.subscribeInteractionClass(routePlanning);
                    break;
                case 4:
                    rtiAmbassador.subscribeInteractionClass(early_warningRadar);
                    rtiAmbassador.subscribeInteractionClass(trackingRadar);
                    rtiAmbassador.subscribeInteractionClass(missionDistribution);
                    break;
                case 5:
                    rtiAmbassador.subscribeInteractionClass(anti_aircraftMissile);
                    break;
                default:
                    rtiAmbassador.subscribeInteractionClass(communication);
                    break;
            }
        } catch (RTIexception ignored) {

        }
    }
}
