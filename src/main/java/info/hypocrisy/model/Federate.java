package info.hypocrisy.model;

import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAunicodeString;
import hla.rti1516e.encoding.DecoderException;
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
public class Federate extends NullFederateAmbassador implements Runnable{
    private boolean state = true;
    private boolean status = true;
    private RTIambassador _rtiAmbassador;
    private InteractionClassHandle _messageId;
    private ParameterHandle _parameterIdText;
    private ParameterHandle _parameterIdSender;
    private ObjectInstanceHandle _userId;
    private AttributeHandle _attributeIdName;
    private String _username;

    private volatile boolean _reservationComplete;
    private volatile boolean _reservationSucceeded;
    private final Object _reservationSemaphore = new Object();

    private EncoderFactory _encoderFactory;

    private final Map<ObjectInstanceHandle, Participant> _knownObjects = new HashMap<ObjectInstanceHandle, Participant>();

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

    private FederateParameters federateParameters;
    public Federate(FederateParameters federateParameters) {
        this.federateParameters = federateParameters;
        this.timeToMoveTo = new HLAfloat64TimeImpl(0);
    }

    public boolean getState() {
        return state;
    }
    public void setState(boolean state) {
        this.state = state;
    }
    public boolean getStatus() {
        return status;
    }
    public void setStatus(boolean status) {
        this.status = status;
    }

    public FederateAttributes getFederateAttributes() {
        FederateAttributes federateAttributes = new FederateAttributes();
        federateAttributes.setName(federateParameters.getFederateName());
        federateAttributes.setFederation(federateParameters.getFederationName());
        federateAttributes.setStrategy(federateParameters.getStrategy());
        federateAttributes.setTime(this.getTimeToMoveTo());
        return federateAttributes;
    }

    private HLAfloat64Time timeToMoveTo;
    private HLAfloat64Interval advancedStep;
    public Double getTimeToMoveTo() {
        return timeToMoveTo.getValue();
    }

    public void createAndJoin() {
        try {
            /**********************
             * get Rti ambassador and connect with it.
             **********************/
            try {
                RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
                _rtiAmbassador = rtiFactory.getRtiAmbassador();
                _encoderFactory = rtiFactory.getEncoderFactory();
            } catch (Exception e) {
                System.out.println("Unable to create RTI ambassador.");
                return;
            }

            String crcAddress = federateParameters.getCrcAddress();
            String settingsDesignator = "crcAddress=" + crcAddress;
            _rtiAmbassador.connect(this, CallbackModel.HLA_IMMEDIATE, settingsDesignator);

            /**********************
             * Clean up old federation
             **********************/
            try {
                _rtiAmbassador.destroyFederationExecution(federateParameters.getFederationName());
            } catch (FederatesCurrentlyJoined ignored) {
            } catch (FederationExecutionDoesNotExist ignored) {
            }

            /**********************
             * Create federation
             **********************/
            String s = "http://localhost:8080/assets/config/HLADemo.xml";
            URL url = new URL(s);
            try {
                _rtiAmbassador.createFederationExecution(federateParameters.getFederationName(), new URL[]{url}, "HLAfloat64Time");
            } catch (FederationExecutionAlreadyExists ignored) {
            }

            /**********************
             * Join current federate(specified with this) into current federation(specified with _rtiAmbassador)
             **********************/
            _rtiAmbassador.joinFederationExecution(federateParameters.getFederateName(), federateParameters.getFederationName(), new URL[]{url});

            /**********************
             * Add by Hypocrisy on 03/28/2015
             * Time Management Variables.
             **********************/
            HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl(4);
            //_rtiAmbassador->enableAsynchronousDelivery();
            if( "Regulating".equals(federateParameters.getStrategy()) ) {
                _rtiAmbassador.enableTimeRegulation(lookahead);
            } else if( "Constrained".equals(federateParameters.getStrategy()) ) {
                _rtiAmbassador.enableTimeConstrained();
            } else {
                _rtiAmbassador.enableTimeRegulation(lookahead);
                _rtiAmbassador.enableTimeConstrained();
            }
            timeToMoveTo = new HLAfloat64TimeImpl(0);
            advancedStep = new HLAfloat64IntervalImpl(2);
            _rtiAmbassador.enableCallbacks();

        } catch (Exception e) {
            System.out.println("Unable to join");
        }
    }

    //private AdvanceTime advanceTime;
    //protected Timer timer = new Timer();
    @Override
    public void run() {
        try {
            /**********************
             * Subscribe and publish interactions
             **********************/
            _messageId = _rtiAmbassador.getInteractionClassHandle("Communication");
            _parameterIdText = _rtiAmbassador.getParameterHandle(_messageId, "Message");
            _parameterIdSender = _rtiAmbassador.getParameterHandle(_messageId, "Sender");

            _rtiAmbassador.subscribeInteractionClass(_messageId);
            _rtiAmbassador.publishInteractionClass(_messageId);

            /**********************
             * Subscribe and publish objects
             **********************/
            ObjectClassHandle participantId = _rtiAmbassador.getObjectClassHandle("Participant");
            _attributeIdName = _rtiAmbassador.getAttributeHandle(participantId, "Name");

            AttributeHandleSet attributeSet = _rtiAmbassador.getAttributeHandleSetFactory().create();
            attributeSet.add(_attributeIdName);

            _rtiAmbassador.subscribeObjectClassAttributes(participantId, attributeSet);
            _rtiAmbassador.publishObjectClassAttributes(participantId, attributeSet);

            /**********************
             * Reserve object instance name and register object instance
             **********************/
            do {
                Calendar cal = Calendar.getInstance();
                _username = "hecate" + cal.get(Calendar.SECOND);

                try {
                    _reservationComplete = false;
                    _rtiAmbassador.reserveObjectInstanceName(_username);
                    synchronized (_reservationSemaphore) {
                        // Wait for response from RTI
                        while (!_reservationComplete) {
                            try {
                                _reservationSemaphore.wait();
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                    if (!_reservationSucceeded) {
                        System.out.println("Name already taken, try again.");
                        return;
                    }
                } catch (IllegalName e) {
                    //System.out.println("Illegal name. Try again.");
                } catch (RTIexception e) {
                    //System.out.println("RTI exception when reserving name: " + e.getMessage());
                    return;
                }
            } while (!_reservationSucceeded);

            _userId = _rtiAmbassador.registerObjectInstance(participantId, _username);

            //advanceTime = new AdvanceTime(timeToMoveTo,advancedStep,_rtiAmbassador);
            //timer.schedule(advanceTime, 0, 2000);
            while(state) {
                if(status) {
                    try {
                        Thread.sleep(500);
                        timeToMoveTo = timeToMoveTo.add(advancedStep);
                        _rtiAmbassador.timeAdvanceRequest(timeToMoveTo);
                    } catch (Exception e) {
                        timeToMoveTo = timeToMoveTo.subtract(advancedStep);
                    }

                    HLAunicodeString nameEncoder = _encoderFactory.createHLAunicodeString(_username);

                    String message = "Hello";

                    ParameterHandleValueMap parameters = _rtiAmbassador.getParameterHandleValueMapFactory().create(1);
                    HLAunicodeString messageEncoder = _encoderFactory.createHLAunicodeString();
                    messageEncoder.setValue(message);
                    parameters.put(_parameterIdText, messageEncoder.toByteArray());
                    parameters.put(_parameterIdSender, nameEncoder.toByteArray());
                    _rtiAmbassador.sendInteraction(_messageId, parameters, null);
                    //_rtiAmbassador.sendInteraction(_messageId, parameters, null, timeToMoveTo);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void update() {

    }

    public void destroy() {
        try {
            _rtiAmbassador.resignFederationExecution(ResignAction.DELETE_OBJECTS_THEN_DIVEST);
            try {
                _rtiAmbassador.destroyFederationExecution(federateParameters.getFederationName());
            } catch (FederatesCurrentlyJoined ignored) {
            }
            _rtiAmbassador.disconnect();
            _rtiAmbassador = null;
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
        if (interactionClass.equals(_messageId)) {
            if (!theParameters.containsKey(_parameterIdText)) {
                System.out.println("Bad message received: No text.");
                return;
            }
            if (!theParameters.containsKey(_parameterIdSender)) {
                System.out.println("Bad message received: No sender.");
                return;
            }
            try {
                HLAunicodeString messageDecoder = _encoderFactory.createHLAunicodeString();
                HLAunicodeString senderDecoder = _encoderFactory.createHLAunicodeString();
                messageDecoder.decode(theParameters.get(_parameterIdText));
                senderDecoder.decode(theParameters.get(_parameterIdSender));
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
        synchronized (_reservationSemaphore) {
            _reservationComplete = true;
            _reservationSucceeded = true;
            _reservationSemaphore.notifyAll();
        }
    }

    @Override
    public final void objectInstanceNameReservationFailed(String objectName) {
        synchronized (_reservationSemaphore) {
            _reservationComplete = true;
            _reservationSucceeded = false;
            _reservationSemaphore.notifyAll();
        }
    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] userSuppliedTag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo) {
        Participant member = _knownObjects.remove(theObject);
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
        if (!_knownObjects.containsKey(theObject)) {
            if (theAttributes.containsKey(_attributeIdName)) {
                try {
                    final HLAunicodeString usernameDecoder = _encoderFactory.createHLAunicodeString();
                    usernameDecoder.decode(theAttributes.get(_attributeIdName));
                    String memberName = usernameDecoder.getValue();
                    Participant member = new Participant(memberName);
                    System.out.println("[" + member + " has joined]");
                    System.out.print("> ");
                    _knownObjects.put(theObject, member);
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
        if (theObject.equals(_userId) && theAttributes.contains(_attributeIdName)) {
            try {
                AttributeHandleValueMap attributeValues = _rtiAmbassador.getAttributeHandleValueMapFactory().create(1);
                HLAunicodeString nameEncoder = _encoderFactory.createHLAunicodeString(_username);
                attributeValues.put(_attributeIdName, nameEncoder.toByteArray());
                _rtiAmbassador.updateAttributeValues(_userId, attributeValues, null);
            } catch (RTIexception ignored) {
            }
        }
    }

    @Override
    public void timeAdvanceGrant(LogicalTime logicalTime) {
    }

    class AdvanceTime extends TimerTask {
        private HLAfloat64Time timeToMoveTo;
        private HLAfloat64Interval advancedStep;
        private RTIambassador rtiAmbassador;
        public AdvanceTime(HLAfloat64Time timeToMoveTo, HLAfloat64Interval advancedStep, RTIambassador rtiAmbassador) {
            this.timeToMoveTo = timeToMoveTo;
            this.advancedStep = advancedStep;
            this.rtiAmbassador = rtiAmbassador;
        }

        public HLAfloat64Time getTimeToMoveTo() {
            return timeToMoveTo;
        }
        @Override
        public void run() {
            try {
                timeToMoveTo = timeToMoveTo.add(advancedStep);
                rtiAmbassador.timeAdvanceRequest(timeToMoveTo);
            } catch (IllegalTimeArithmetic illegalTimeArithmetic) {
                illegalTimeArithmetic.printStackTrace();
            } catch (LogicalTimeAlreadyPassed logicalTimeAlreadyPassed) {
                logicalTimeAlreadyPassed.printStackTrace();
            } catch (RequestForTimeRegulationPending requestForTimeRegulationPending) {
                requestForTimeRegulationPending.printStackTrace();
            } catch (SaveInProgress saveInProgress) {
                saveInProgress.printStackTrace();
            } catch (InvalidLogicalTime invalidLogicalTime) {
                invalidLogicalTime.printStackTrace();
            } catch (InTimeAdvancingState inTimeAdvancingState) {
                inTimeAdvancingState.printStackTrace();
            } catch (RestoreInProgress restoreInProgress) {
                restoreInProgress.printStackTrace();
            } catch (RequestForTimeConstrainedPending requestForTimeConstrainedPending) {
                requestForTimeConstrainedPending.printStackTrace();
            } catch (RTIinternalError rtIinternalError) {
                rtIinternalError.printStackTrace();
            } catch (FederateNotExecutionMember federateNotExecutionMember) {
                federateNotExecutionMember.printStackTrace();
            } catch (NotConnected notConnected) {
                notConnected.printStackTrace();
            }
        }
    }
}
