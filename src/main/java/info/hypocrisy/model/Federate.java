package info.hypocrisy.model;

import com.sun.org.apache.bcel.internal.generic.VariableLengthInstruction;
import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAunicodeString;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import hla.time1516.LogicalTimeLong;
import se.pitch.prti1516e.time.HLAfloat64IntervalImpl;
import se.pitch.prti1516e.time.HLAfloat64TimeImpl;

import java.util.*;
import java.net.URL;
/**
 * Created by Hypocrisy on 3/23/2016.
 * This class implements a NullFederateAmbassador.
 */
public class Federate extends NullFederateAmbassador implements Runnable{
    private volatile boolean state = true;                       // if false, a thread should be destroyed.
    private volatile boolean status = false;                     // if false, a thread should pause, or we can say it doing nothing.
    private boolean isRegulating = false;               // flag to mark if this federate is Regulating
    private boolean isConstrained = false;              // flag to mark if this federate is Constrained

    private RTIambassador rtiAmbassador;
    private InteractionClassHandle messageId;
    private ParameterHandle paramterIdText;
    private ParameterHandle parameterIdSender;
    private ObjectInstanceHandle userId;
    private AttributeHandle attributeIdName;
    private String username;

    private volatile boolean reservationComplete;
    private volatile boolean reservationSucceeded;
    private final Object reservationSemaphore = new Object();

    private final String SYNC_POINT = "ReadyToRun";
    private volatile boolean isRegisterSyncPointSucceeded = false;
    private volatile boolean isSynchronized = false;

    private EncoderFactory encoderFactory;

    private final Map<ObjectInstanceHandle, Participant> knownObjects = new HashMap<ObjectInstanceHandle, Participant>();

    HLAfloat64Interval realTimeOffset = new HLAfloat64IntervalImpl(0.00);
    private HLAfloat64Time realTime = new HLAfloat64TimeImpl(0.00);
    public void setRealTimeOffset(String realTimeOffset) {
        this.realTimeOffset = new HLAfloat64IntervalImpl(Double.parseDouble(realTimeOffset)-currentTime.getValue());
    }
    public void setRealTime(String realTime) {
        this.realTime = new HLAfloat64TimeImpl(Double.parseDouble(realTime));
    }

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

    public Federate() {
        // For test
        federateAttributes = new FederateAttributes();
        federateAttributes.setName("TestFederate");
        federateAttributes.setFederation("TestFederation");
        federateAttributes.setCrcAddress("localhost");
        federateAttributes.setMechanism(1);
        federateAttributes.setFomName("HLADemo");
        federateAttributes.setFomUrl("http://localhost:8080/assets/config/HLADemo.xml");
        federateAttributes.setStrategy("Regulating and Constrained");
        //federateAttributes.setTime(this.getTimeToMoveTo());
        //federateAttributes.setStatus(status);
        federateAttributes.setStep("1");
        federateAttributes.setLookahead("1");
    }

    private FederateAttributes federateAttributes;
    public Federate(FederateParameters federateParameters) {
        federateAttributes = new FederateAttributes();
        federateAttributes.setName(federateParameters.getFederateName());
        federateAttributes.setFederation(federateParameters.getFederationName());
        federateAttributes.setCrcAddress(federateParameters.getCrcAddress());
        String[] tmp = federateParameters.getFomUrl().split("/");
        federateAttributes.setFomName(tmp[tmp.length - 1]);

        if( "Time Stepped".equals(federateParameters.getMechanism()) ) {
            federateAttributes.setMechanism(0);
        } else if( "Event Driven".equals(federateParameters.getMechanism()) ) {
            federateAttributes.setMechanism(1);
        } else if( "Real Time".equals(federateParameters.getMechanism()) ) {
            federateAttributes.setMechanism(2);
        } else {
            federateAttributes.setMechanism(3);
        }

        federateAttributes.setFomUrl(federateParameters.getFomUrl());
        federateAttributes.setStrategy(federateParameters.getStrategy());
        //federateAttributes.setTime(this.getTimeToMoveTo());
        //federateAttributes.setStatus(status);
        federateAttributes.setStep(federateParameters.getStep());
        federateAttributes.setLookahead(federateParameters.getLookahead());
    }

    public void setState(boolean state) {
        this.state = state;
    }
    public void setStatus(boolean status) {
        this.status = status;
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
            // If there is constrained federates, and this federate is regulating, the start time of this federate is not 0.
            currentTime = (HLAfloat64Time) rtiAmbassador.queryLogicalTime();
            advancedStep = new HLAfloat64IntervalImpl( Double.parseDouble(federateAttributes.getStep()) );
            rtiAmbassador.enableCallbacks();
        } catch (Exception e) {
            System.out.println("Unable to join");
        }
        try {
            /**********************
             * Subscribe and publish interactions
             **********************/
            messageId = rtiAmbassador.getInteractionClassHandle("Communication");
            paramterIdText = rtiAmbassador.getParameterHandle(messageId, "Message");
            parameterIdSender = rtiAmbassador.getParameterHandle(messageId, "Sender");

            rtiAmbassador.subscribeInteractionClass(messageId);
            rtiAmbassador.publishInteractionClass(messageId);

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
        } catch (RTIexception ignored) {

        }
    }

    public void update(UpdateParameters updateParameters) {
        federateAttributes.setStrategy(updateParameters.getStrategy());
        federateAttributes.setStep(updateParameters.getStep());
        federateAttributes.setLookahead(updateParameters.getLookahead());

        HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl( Double.parseDouble(updateParameters.getLookahead()) );
        //rtiAmbassador->enableAsynchronousDelivery();
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
        } catch (Exception e) {

        }
        advancedStep = new HLAfloat64IntervalImpl( Double.parseDouble(updateParameters.getStep()) );
    }

    public boolean isFirst = true;
    @Override
    public void run() {
        try {
            while(state) {
                Thread.sleep(1000);
                if(status && federateAttributes.getMechanism() == 0) {
                    HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(username);

                    String message = "Hello";

                    ParameterHandleValueMap parameters = rtiAmbassador.getParameterHandleValueMapFactory().create(1);
                    HLAunicodeString messageEncoder = encoderFactory.createHLAunicodeString();
                    messageEncoder.setValue(message);
                    parameters.put(paramterIdText, messageEncoder.toByteArray());
                    parameters.put(parameterIdSender, nameEncoder.toByteArray());
                    rtiAmbassador.sendInteraction(messageId, parameters, null);
                    //rtiAmbassador.sendInteraction(messageId, parameters, null, currentTime.add(advancedStep));

                    try {
                        rtiAmbassador.timeAdvanceRequest(currentTime.add(advancedStep));
                    } catch (Exception ignored) {
                    }
                }

                if(status && federateAttributes.getMechanism() == 1) {
                    HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(username);

                    String message = "Hello";

                    ParameterHandleValueMap parameters = rtiAmbassador.getParameterHandleValueMapFactory().create(1);
                    HLAunicodeString messageEncoder = encoderFactory.createHLAunicodeString();
                    messageEncoder.setValue(message);
                    parameters.put(paramterIdText, messageEncoder.toByteArray());
                    parameters.put(parameterIdSender, nameEncoder.toByteArray());

                    HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl(Double.parseDouble(federateAttributes.getLookahead()));
                    HLAfloat64Time timestamp = currentTime.add(lookahead);
                    rtiAmbassador.sendInteraction(messageId, parameters, null, timestamp);

                    try {
                        rtiAmbassador.nextMessageRequest(currentTime.add(advancedStep));
                    } catch (Exception e) {
                    }
                }

                if(status && federateAttributes.getMechanism() == 2) {
                    HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(username);

                    String message = "Hello";

                    ParameterHandleValueMap parameters = rtiAmbassador.getParameterHandleValueMapFactory().create(1);
                    HLAunicodeString messageEncoder = encoderFactory.createHLAunicodeString();
                    messageEncoder.setValue(message);
                    parameters.put(paramterIdText, messageEncoder.toByteArray());
                    parameters.put(parameterIdSender, nameEncoder.toByteArray());

                    rtiAmbassador.sendInteraction(messageId, parameters, null);
                    //HLAfloat64Time timestamp = currentTime;
                    //rtiAmbassador.sendInteraction(messageId, parameters, null, timestamp);

                    try {
                        rtiAmbassador.timeAdvanceRequest(realTime.subtract(realTimeOffset));
                        System.out.println(realTime.subtract(realTimeOffset).getValue());
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void test() {
        try {
            int i = 1;
            while((i--)>0) {
                try {
                    HLAunicodeString nameEncoder = encoderFactory.createHLAunicodeString(username);

                    String message = "Hello";

                    ParameterHandleValueMap parameters = rtiAmbassador.getParameterHandleValueMapFactory().create(1);
                    HLAunicodeString messageEncoder = encoderFactory.createHLAunicodeString();
                    messageEncoder.setValue(message);
                    parameters.put(paramterIdText, messageEncoder.toByteArray());
                    parameters.put(parameterIdSender, nameEncoder.toByteArray());

                    Double epsilon = 0.00001;
                    HLAfloat64Interval ts = new HLAfloat64IntervalImpl(Double.parseDouble(federateAttributes.getLookahead())+0.5);
                    HLAfloat64Time timestamp = currentTime.add(ts);
                    rtiAmbassador.sendInteraction(messageId, parameters, null, timestamp);

                    try {
                        rtiAmbassador.nextMessageRequest(currentTime.add(advancedStep));
                    } catch (Exception e) {
                    }
                } catch (RTIexception e) {
                    e.printStackTrace();
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
        if (interactionClass.equals(messageId)) {
            if (!theParameters.containsKey(paramterIdText)) {
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
                messageDecoder.decode(theParameters.get(paramterIdText));
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
        System.out.println("Receive message with timestamp: " + theTime.toString());
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
        System.out.println("Receive message with timestamp: " + theTime.toString());
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
        System.out.println("Current Logical Time: " + logicalTime.toString());
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime logicalTime) throws FederateInternalError {
        isConstrained = true;
        System.out.println("Current Logical Time: " + logicalTime.toString());
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
        System.out.println("Time Advance to " + logicalTime.toString() + " Successfully");
    }
}
