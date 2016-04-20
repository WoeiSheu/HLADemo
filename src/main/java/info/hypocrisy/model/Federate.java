package info.hypocrisy.model;

import com.sun.management.MissionControl;
import hla.rti1516e.*;
import hla.rti1516e.encoding.*;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
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
    private InteractionClassHandle cruiseMissile;
    private ParameterHandle cruiseMissileStatus;
    private ParameterHandle cruiseMissilePosition;
    private InteractionClassHandle early_warningRadar;
    private ParameterHandle early_warningRadarStatus;
    private ParameterHandle early_warningRadarPosition;
    private InteractionClassHandle missionDistribution;
    private ParameterHandle strategyOfMissionDistribution;
    private InteractionClassHandle anti_aircraftMissile;
    private ParameterHandle anti_aircraftStatus;
    private ParameterHandle anti_aircraftMissilePosition;
    private InteractionClassHandle routePlanning;
    private ParameterHandle route;
    private InteractionClassHandle trackingRadar;
    private ParameterHandle trackingRadarStatus;
    private ParameterHandle trackingRadarPosition;
    private InteractionClassHandle messageId;
    private ParameterHandle parameterIdText;
    private ParameterHandle parameterIdSender;
    private InteractionClassHandle[] interactionClasses;
    /**********************
     * All object instance handle and their attributes' handle
     **********************/
    private ObjectInstanceHandle userId;
    private AttributeHandle attributeIdName;
    private String username;

    private volatile boolean reservationComplete;
    private volatile boolean reservationSucceeded;
    private final Object reservationSemaphore = new Object();

    private final Map<ObjectInstanceHandle, Participant> knownObjects = new HashMap<ObjectInstanceHandle, Participant>();
    /**********************
     * Sync variables.
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
     * Participant for object instance.
     **********************/
    private static class Participant {
        private final String name;

        Participant(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
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

        switch (federateParameters.getType()) {
            case "Cruise Missile":
                federateAttributes.setType(0);
                break;
            case "Early-warning Radar":
                federateAttributes.setType(1);
                break;
            case "Mission Distribution":
                federateAttributes.setType(2);
                break;
            case "Anti-aircraft Missile":
                federateAttributes.setType(3);
                break;
            case "Route Planning":
                federateAttributes.setType(4);
                break;
            case "Tracking Radar":
                federateAttributes.setType(5);
                break;
            default:
                federateAttributes.setType(6);
        }

        String[] tmp = federateParameters.getFomUrl().split("/");
        federateAttributes.setFomName(tmp[tmp.length - 1]);

        if( "Time Stepped".equals(federateParameters.getMechanism()) ) {
            federateAttributes.setMechanism(0);
        } else if( "Event Driven".equals(federateParameters.getMechanism()) ) {
            federateAttributes.setMechanism(1);
        } else {
            federateAttributes.setMechanism(2);
        }

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

    public void createAndJoin() {
        try {
            /**********************
             * get Rti ambassador and connect with it.
             **********************/
            try {
                RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
                rtiAmbassador = rtiFactory.getRtiAmbassador();
                encoderFactory = rtiFactory.getEncoderFactory();
            } catch (Exception e) {
                System.out.println("Unable to create RTI ambassador.");
                return;
            }
            String crcAddress = federateAttributes.getCrcAddress();
            String settingsDesignator = "crcAddress=" + crcAddress;
            rtiAmbassador.connect(this, CallbackModel.HLA_IMMEDIATE, settingsDesignator);

            /**********************
             * Clean up old federation
             **********************/
            try {
                rtiAmbassador.destroyFederationExecution(federateAttributes.getFederation());
            } catch (FederatesCurrentlyJoined ignored) {
            } catch (FederationExecutionDoesNotExist ignored) {
            }

            /**********************
             * Create federation
             **********************/
            //String s = "http://localhost:8080/assets/config/HLADemo.xml";
            URL url = new URL(federateAttributes.getFomUrl());
            try {
                rtiAmbassador.createFederationExecution(federateAttributes.getFederation(), new URL[]{url}, "HLAfloat64Time");
            } catch (FederationExecutionAlreadyExists ignored) {
            }

            /**********************
             * Join current federate(specified with this) into current federation(specified with rtiAmbassador)
             **********************/
            rtiAmbassador.joinFederationExecution(federateAttributes.getName(), federateAttributes.getFederation(), new URL[]{url});

            /**********************
             * Add by Hypocrisy on 03/28/2015
             * Time Management Variables.
             **********************/
            HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl( Double.parseDouble(federateAttributes.getLookahead()) );
            //rtiAmbassador->enableAsynchronousDelivery();
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
            System.out.println("Unable to join");
        }
        try {
            /**********************
             * Subscribe and publish interactions
             **********************/
            cruiseMissile = rtiAmbassador.getInteractionClassHandle("CruiseMissile");
            cruiseMissileStatus = rtiAmbassador.getParameterHandle(cruiseMissile,"Status");
            cruiseMissilePosition = rtiAmbassador.getParameterHandle(cruiseMissile,"Position");
            early_warningRadar = rtiAmbassador.getInteractionClassHandle("Early_warningRadar");
            early_warningRadarStatus = rtiAmbassador.getParameterHandle(early_warningRadar,"Status");
            early_warningRadarPosition = rtiAmbassador.getParameterHandle(early_warningRadar,"Position");
            missionDistribution = rtiAmbassador.getInteractionClassHandle("MissionDistribution");
            strategyOfMissionDistribution = rtiAmbassador.getParameterHandle(missionDistribution,"Strategy");
            anti_aircraftMissile = rtiAmbassador.getInteractionClassHandle("Anti_aircraftMissile");
            anti_aircraftStatus = rtiAmbassador.getParameterHandle(anti_aircraftMissile,"Status");
            anti_aircraftMissilePosition = rtiAmbassador.getParameterHandle(anti_aircraftMissile,"Position");
            routePlanning = rtiAmbassador.getInteractionClassHandle("RoutePlanning");
            route = rtiAmbassador.getParameterHandle(routePlanning,"Route");
            trackingRadar = rtiAmbassador.getInteractionClassHandle("TrackingRadar");
            trackingRadarStatus = rtiAmbassador.getParameterHandle(trackingRadar,"Status");
            trackingRadarPosition = rtiAmbassador.getParameterHandle(trackingRadar,"Position");
            messageId = rtiAmbassador.getInteractionClassHandle("Communication");
            parameterIdText = rtiAmbassador.getParameterHandle(messageId, "Message");
            parameterIdSender = rtiAmbassador.getParameterHandle(messageId, "Sender");

            interactionClasses = new InteractionClassHandle[]{cruiseMissile,early_warningRadar,missionDistribution,anti_aircraftMissile,routePlanning,trackingRadar,messageId};
            switch (federateAttributes.getType()) {
                case 0:
                    rtiAmbassador.publishInteractionClass(cruiseMissile);
                    break;
                case 1:
                    rtiAmbassador.publishInteractionClass(early_warningRadar);
                    rtiAmbassador.subscribeInteractionClass(cruiseMissile);
                    break;
                case 2:
                    rtiAmbassador.publishInteractionClass(missionDistribution);
                    rtiAmbassador.subscribeInteractionClass(early_warningRadar);
                    rtiAmbassador.subscribeInteractionClass(trackingRadar);
                    break;
                case 3:
                    rtiAmbassador.publishInteractionClass(anti_aircraftMissile);
                    rtiAmbassador.subscribeInteractionClass(routePlanning);
                    break;
                case 4:
                    rtiAmbassador.publishInteractionClass(routePlanning);
                    rtiAmbassador.subscribeInteractionClass(early_warningRadar);
                    rtiAmbassador.subscribeInteractionClass(trackingRadar);
                    rtiAmbassador.subscribeInteractionClass(missionDistribution);
                    break;
                case 5:
                    rtiAmbassador.publishInteractionClass(trackingRadar);
                    rtiAmbassador.subscribeInteractionClass(anti_aircraftMissile);
                    break;
                default:
                    rtiAmbassador.subscribeInteractionClass(messageId);
                    rtiAmbassador.publishInteractionClass(messageId);
                    break;
            }

            /**********************
             * Subscribe and publish objects
             **********************/
            ObjectClassHandle participantId = rtiAmbassador.getObjectClassHandle("Participant");
            attributeIdName = rtiAmbassador.getAttributeHandle(participantId, "Name");

            AttributeHandleSet attributeSet = rtiAmbassador.getAttributeHandleSetFactory().create();
            attributeSet.add(attributeIdName);

            rtiAmbassador.subscribeObjectClassAttributes(participantId, attributeSet);
            rtiAmbassador.publishObjectClassAttributes(participantId, attributeSet);

            /**********************
             * Reserve object instance name and register object instance
             **********************/
            do {
                Date date = new Date();
                username = "f" + new Long(date.getTime()).toString();

                try {
                    reservationComplete = false;
                    rtiAmbassador.reserveObjectInstanceName(username);
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
                        System.out.println("Name already taken, try again.");
                        return;
                    }
                } catch (IllegalName e) {
                    //System.out.println("Illegal name. Try again.");
                } catch (RTIexception e) {
                    //System.out.println("RTI exception when reserving name: " + e.getMessage());
                    return;
                }
            } while (!reservationSucceeded);
            userId = rtiAmbassador.registerObjectInstance(participantId, username);

            /**********************
             * Register Synchronization Point
             **********************/
            rtiAmbassador.enableAsynchronousDelivery();
            byte[] tag = {};
            try {
                rtiAmbassador.registerFederationSynchronizationPoint(SYNC_POINT, tag);
            } catch (Exception e) {

            }
            try {
                rtiAmbassador.synchronizationPointAchieved(SYNC_POINT);
            } catch (RTIexception e) {

            }

            // If there is constrained federates, and this federate is regulating, the start time of this federate is not 0.
            currentTime = (HLAfloat64Time) rtiAmbassador.queryLogicalTime();
            advancedStep = new HLAfloat64IntervalImpl( Double.parseDouble(federateAttributes.getStep()) );
        } catch (RTIexception ignored) {

        }
    }

    public void update(UpdateParameters updateParameters) {
        federateAttributes.setStrategy(updateParameters.getStrategy());
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
        try {
            ParameterHandleValueMap parameters = rtiAmbassador.getParameterHandleValueMapFactory().create(1);

            HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(username);
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
                    parameters.put(parameterIdText, messageEncoder.toByteArray());
                    parameters.put(parameterIdSender, nameEncoder.toByteArray());
                    break;
            }
            return parameters;
        } catch (RTIexception e) {

        }
        return null;
    }

    public boolean isFirst = true;
    @Override
    public void run() {
        try {
            while(state) {
                Thread.sleep(1000);

                ParameterHandleValueMap parameters = setParameters();

                if(status && !isPhysicalDevice && federateAttributes.getMechanism() == 0) {
                    //rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()],parameters,null);
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
                    //rtiAmbassador.sendInteraction(interactionClasses[federateAttributes.getType()], parameters, null);
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
            }
        } catch (Exception ignored) {
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
            //timer.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

        if(interactionClass.equals(cruiseMissile)) {
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

        if(interactionClass.equals(early_warningRadar)) {
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

        if(interactionClass.equals(missionDistribution)) {
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

        if(interactionClass.equals(anti_aircraftMissile)) {
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

        if(interactionClass.equals(routePlanning)) {
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

        if(interactionClass.equals(trackingRadar)) {
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

        if (interactionClass.equals(messageId)) {
            if (!theParameters.containsKey(parameterIdText)) {
                System.out.println("Bad message received: No text.");
                return;
            }
            if (!theParameters.containsKey(parameterIdSender)) {
                System.out.println("Bad message received: No sender.");
                return;
            }
            try {
                HLAunicodeString messageDecoder = encoderFactory.createHLAunicodeString();
                HLAunicodeString senderDecoder = encoderFactory.createHLAunicodeString();
                messageDecoder.decode(theParameters.get(parameterIdText));
                senderDecoder.decode(theParameters.get(parameterIdSender));
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
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {

        System.out.println(this.federateAttributes.getName() + ": " + theTime.toString());

        HLAinteger16LE statusDecoder = encoderFactory.createHLAinteger16LE();
        HLAunicodeString positionDecoder = encoderFactory.createHLAunicodeString();
        HLAvariableArray variableArrayDecoder = encoderFactory.createHLAvariableArray(factory);

        if(interactionClass.equals(cruiseMissile)) {
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

        if(interactionClass.equals(early_warningRadar)) {
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

        if(interactionClass.equals(missionDistribution)) {
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

        if(interactionClass.equals(anti_aircraftMissile)) {
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

        if(interactionClass.equals(routePlanning)) {
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

        if(interactionClass.equals(trackingRadar)) {
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

        if (interactionClass.equals(messageId)) {
            if (!theParameters.containsKey(parameterIdText)) {
                System.out.println("Bad message received: No text.");
                return;
            }
            if (!theParameters.containsKey(parameterIdSender)) {
                System.out.println("Bad message received: No sender.");
                return;
            }
            try {
                HLAunicodeString messageDecoder = encoderFactory.createHLAunicodeString();
                HLAunicodeString senderDecoder = encoderFactory.createHLAunicodeString();
                messageDecoder.decode(theParameters.get(parameterIdText));
                senderDecoder.decode(theParameters.get(parameterIdSender));
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
        Participant member = knownObjects.remove(theObject);
        if (member != null) {
            System.out.println("[" + member + " has left]");
        }
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] userSuppliedTag,
                                       OrderType sentOrdering,
                                       TransportationTypeHandle theTransport,
                                       SupplementalReflectInfo reflectInfo) {
        if (!knownObjects.containsKey(theObject)) {
            if (theAttributes.containsKey(attributeIdName)) {
                try {
                    final HLAunicodeString usernameDecoder = encoderFactory.createHLAunicodeString();
                    usernameDecoder.decode(theAttributes.get(attributeIdName));
                    String memberName = usernameDecoder.getValue();
                    Participant member = new Participant(memberName);
                    System.out.println("[" + member + " has joined]");
                    System.out.print("> ");
                    knownObjects.put(theObject, member);
                } catch (DecoderException e) {
                    System.out.println("Failed to decode incoming attribute");
                }
            }
        }
    }

    @Override
    public final void provideAttributeValueUpdate(ObjectInstanceHandle theObject,
                                                  AttributeHandleSet theAttributes,
                                                  byte[] userSuppliedTag) {
        if (theObject.equals(userId) && theAttributes.contains(attributeIdName)) {
            try {
                AttributeHandleValueMap attributeValues = rtiAmbassador.getAttributeHandleValueMapFactory().create(1);
                HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(username);
                attributeValues.put(attributeIdName, nameEncoder.toByteArray());
                rtiAmbassador.updateAttributeValues(userId, attributeValues, null);
            } catch (RTIexception ignored) {
            }
        }
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
}
