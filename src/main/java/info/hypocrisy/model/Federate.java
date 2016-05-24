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
import java.util.concurrent.ThreadFactory;

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
    private ParameterHandle groupBLocation;
    private ParameterHandle groupCLocation;
    private ParameterHandle groupEResult;
    private ParameterHandle groupFRoute;
    private ParameterHandle StatusValue;
    private ParameterHandle MomentValue;

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
            //objectInstanceId = rtiAmbassador.registerObjectInstance(objectClassHandles[federateAttributes.getType()], name);

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

    public void sendMessages() {
        // To be fixed
        try {
            ParameterHandleValueMap parameters = rtiAmbassador.getParameterHandleValueMapFactory().create(1);
            HLAunicodeString stringEncoder = encoderFactory.createHLAunicodeString();

            HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl(Double.parseDouble(federateAttributes.getLookahead()));
            HLAfloat64Time timestamp;
            if(federateAttributes.getType() == 6) {
                timestamp = realTime.subtract(realTimeOffset).add(lookahead);
            } else {
                timestamp = currentTime.add(lookahead).add(advancedStep);
            }

            switch (federateAttributes.getType()) {
                case 0:
                    break;
                case 1:
                    stringEncoder.setValue("Location of B");
                    parameters.put(groupBLocation, stringEncoder.toByteArray());
                    break;
                case 2:
                    stringEncoder.setValue("Location of C");
                    parameters.put(groupCLocation, stringEncoder.toByteArray());
                    break;
                case 3:
                    break;
                case 4:
                    stringEncoder.setValue("Result of E");
                    parameters.put(groupEResult, stringEncoder.toByteArray());
                    stringEncoder.setValue("Status of E");
                    parameters.put(StatusValue, stringEncoder.toByteArray());
                    break;
                case 5:
                    stringEncoder.setValue("Route of F");
                    parameters.put(groupFRoute, stringEncoder.toByteArray());
                    stringEncoder.setValue("Status of F");
                    parameters.put(StatusValue, stringEncoder.toByteArray());
                    break;
                case 6:
                    stringEncoder.setValue("Moment of I");
                    parameters.put(MomentValue, stringEncoder.toByteArray());

                    break;
                default:
                    break;
            }
        } catch (RTIexception ignored) {
        }
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
            while(state) {
                // update object  attributes
                // end of object attributes update.

                // send interactions and request time advancement
                sendMessages();

                // Time Advance
                if(status) {
                    try {
                        switch (federateAttributes.getType()) {
                            case 0:
                                rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                                break;
                            case 1:
                                rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                                break;
                            case 2:
                                rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                                break;
                            case 3:
                                rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                                break;
                            case 4:
                                if(currentTime.getValue() > 59 && currentTime.getValue() < 61) {
                                    Thread.sleep(20000);
                                }
                                rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                                break;
                            case 5:
                                if(currentTime.getValue() > 79 && currentTime.getValue() < 81) {
                                    Thread.sleep(20000);
                                }
                                rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                                break;
                            case 6:
                                rtiAmbassador.timeAdvanceRequest(realTime.subtract(realTimeOffset));
                                break;
                            default:
                                rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                                break;
                        }
                    } catch (RTIexception e) {
                        //
                    }
                }
                /*
                if(status && !isPhysicalDevice && federateAttributes.getMechanism() == 0) {
                    try {
                        rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                    } catch (Exception ignored) {
                    }
                }

                if(status && !isPhysicalDevice && federateAttributes.getMechanism() == 1) {
                    try {
                        rtiAmbassador.nextMessageRequest(currentTime.add(advancedStep));
                    } catch (Exception ignored) {
                        //e.printStackTrace();
                    }
                }

                if(status && isPhysicalDevice && federateAttributes.getMechanism() == 0) {
                    try {
                        rtiAmbassador.timeAdvanceRequest(realTime.subtract(realTimeOffset));
                    } catch (Exception ignored) {
                    }
                }

                if(status && isPhysicalDevice && federateAttributes.getMechanism() == 1) {
                    try {
                        rtiAmbassador.timeAdvanceRequest(realTime.subtract(realTimeOffset));
                    } catch (Exception ignored) {
                    }
                }
                */
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
            ObjectClassHandle objectGroupA = rtiAmbassador.getObjectClassHandle("GroupA");
            AttributeHandle groupAAttributeLocation = rtiAmbassador.getAttributeHandle(objectGroupA, "location");
            tmp = new ArrayList<>();
            tmp.add(groupAAttributeLocation);
            mapObjectAttributes.put(objectGroupA,tmp);

            //ObjectClassHandle objectGroupB = rtiAmbassador.getObjectClassHandle("GroupB");
            //ObjectClassHandle objectGroupC = rtiAmbassador.getObjectClassHandle("GroupC");

            ObjectClassHandle objectGroupD = rtiAmbassador.getObjectClassHandle("GroupD");
            AttributeHandle groupDAttributeLocation = rtiAmbassador.getAttributeHandle(objectGroupD, "location");
            tmp = new ArrayList<>();
            tmp.add(groupDAttributeLocation);
            mapObjectAttributes.put(objectGroupD,tmp);

            //ObjectClassHandle objectGroupE = rtiAmbassador.getObjectClassHandle("GroupE");
            //ObjectClassHandle objectGroupF = rtiAmbassador.getObjectClassHandle("GroupF");
            //ObjectClassHandle objectGroupI = rtiAmbassador.getObjectClassHandle("GroupI");

            objectClassHandles = new ObjectClassHandle[]{objectGroupA, objectGroupD};

            AttributeHandleSet publishAttributeHandleSet = rtiAmbassador.getAttributeHandleSetFactory().create();
            AttributeHandleSet subscribeAttributeHandleSet = rtiAmbassador.getAttributeHandleSetFactory().create();

            // Publish object attributes.
            // Subscribe object attributes.
            switch (federateAttributes.getType()) {
                case 0:
                    publishAttributeHandleSet.add(groupAAttributeLocation);
                    rtiAmbassador.publishObjectClassAttributes(objectGroupA, publishAttributeHandleSet);
                    break;
                case 1:
                    subscribeAttributeHandleSet.add(groupAAttributeLocation);
                    rtiAmbassador.subscribeObjectClassAttributes(objectGroupA, subscribeAttributeHandleSet);
                    break;
                case 2:
                    subscribeAttributeHandleSet.add(groupAAttributeLocation);
                    rtiAmbassador.subscribeObjectClassAttributes(objectGroupA, subscribeAttributeHandleSet);
                    subscribeAttributeHandleSet.add(groupDAttributeLocation);
                    rtiAmbassador.subscribeObjectClassAttributes(objectGroupD, subscribeAttributeHandleSet);
                    break;
                case 3:
                    publishAttributeHandleSet.add(groupDAttributeLocation);
                    rtiAmbassador.publishObjectClassAttributes(objectGroupD, publishAttributeHandleSet);
                    break;
                case 4:
                    break;
                case 5:
                    break;
                case 6:
                    break;
                default:
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
            InteractionClassHandle groupB = rtiAmbassador.getInteractionClassHandle("GroupB");
            groupBLocation = rtiAmbassador.getParameterHandle(groupB,"location");
            tmp = new ArrayList<>();
            tmp.add(groupBLocation);
            mapInteractionParameters.put(groupB,tmp);

            InteractionClassHandle groupC = rtiAmbassador.getInteractionClassHandle("GroupC");
            groupCLocation = rtiAmbassador.getParameterHandle(groupC,"location");
            tmp = new ArrayList<>();
            tmp.add(groupCLocation);
            mapInteractionParameters.put(groupC,tmp);

            InteractionClassHandle groupE = rtiAmbassador.getInteractionClassHandle("GroupE");
            groupEResult = rtiAmbassador.getParameterHandle(groupE,"result");
            tmp = new ArrayList<>();
            tmp.add(groupEResult);
            mapInteractionParameters.put(groupE,tmp);

            InteractionClassHandle groupF = rtiAmbassador.getInteractionClassHandle("GroupF");
            groupFRoute = rtiAmbassador.getParameterHandle(groupF,"route");
            tmp = new ArrayList<>();
            tmp.add(groupFRoute);
            mapInteractionParameters.put(groupF,tmp);

            InteractionClassHandle Status = rtiAmbassador.getInteractionClassHandle("Status");
            StatusValue = rtiAmbassador.getParameterHandle(Status,"status");
            tmp = new ArrayList<>();
            tmp.add(StatusValue);
            mapInteractionParameters.put(Status,tmp);

            InteractionClassHandle Moment = rtiAmbassador.getInteractionClassHandle("Moment");
            MomentValue = rtiAmbassador.getParameterHandle(Moment,"moment");
            tmp = new ArrayList<>();
            tmp.add(MomentValue);
            mapInteractionParameters.put(Moment,tmp);

            interactionClasses = new InteractionClassHandle[]{groupB,groupC,groupE,groupF,Status,Moment};

            // Publish interactions
            // Subscribe interactions
            switch (federateAttributes.getType()) {
                case 0:
                    rtiAmbassador.subscribeInteractionClass(Moment);
                    break;
                case 1:
                    rtiAmbassador.publishInteractionClass(groupB);

                    rtiAmbassador.subscribeInteractionClass(Moment);
                    break;
                case 2:
                    rtiAmbassador.publishInteractionClass(groupC);

                    rtiAmbassador.subscribeInteractionClass(Moment);
                    break;
                case 3:
                    rtiAmbassador.subscribeInteractionClass(groupF);

                    rtiAmbassador.subscribeInteractionClass(Moment);
                    break;
                case 4:
                    rtiAmbassador.publishInteractionClass(groupE);
                    rtiAmbassador.publishInteractionClass(Status);

                    rtiAmbassador.subscribeInteractionClass(groupB);
                    rtiAmbassador.subscribeInteractionClass(Moment);
                    break;
                case 5:
                    rtiAmbassador.publishInteractionClass(groupF);
                    rtiAmbassador.publishInteractionClass(Status);

                    rtiAmbassador.subscribeInteractionClass(groupC);
                    rtiAmbassador.subscribeInteractionClass(groupE);
                    rtiAmbassador.subscribeInteractionClass(Moment);
                    break;
                case 6:
                    rtiAmbassador.publishInteractionClass(Moment);

                    rtiAmbassador.subscribeInteractionClass(groupF);
                    rtiAmbassador.subscribeInteractionClass(Status);
                    break;
                default:
                    break;
            }
        } catch (RTIexception ignored) {
        }
    }
}
