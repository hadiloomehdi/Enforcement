package ir.ac.ut.ece.rv.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ir.ac.ut.ece.rv.state.central.CentralClassDeclaration;
import ir.ac.ut.ece.rv.state.central.CentralState;
import ir.ac.ut.ece.rv.state.central.DeclareMessageMeta;
import ir.ac.ut.ece.rv.state.monitor.*;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ir.ac.ut.ece.rv.state.central.CentralMsgsrvDeclaration.DECLARE_METHOD;
import static ir.ac.ut.ece.rv.state.monitor.MonitoringMsgsrvDeclaration.MONITORING_MESSAGE;
import static ir.ac.ut.ece.rv.state.monitor.MonitoringMsgsrvDeclaration.REGULAR_MESSAGE;

public class GlobalState {

    public static GlobalState instance = null;
    private Map<String, LocalState> states;
    private DelayManager delayManager;

    public GlobalState(RebecaModel rebecaModel, boolean isStrict) {
        states = rebecaModel
                .getRebecaCode()
                .getMainDeclaration()
                .getMainRebecDefinition()
                .stream()
                .collect(
                        Collectors.toMap(
                                MainRebecDefinition::getName,
                                it -> new LocalState(findReactiveClassDeclaration(it.getType(), rebecaModel), it)
                        )
                );
        createMonitoringActors(isStrict);
        createCentralActor();
        Set<String> vectorClockKeys = states
                .keySet()
                .stream()
                .filter(key -> !(states.get(key) instanceof MonitoringState)).collect(Collectors.toSet());

        states.forEach((instanceName, localState) ->
                localState.initVectorClock(vectorClockKeys, instanceName)
        );
        GlobalState.setInstance(this);
        delayManager = new DelayManager();
    }

    private void createCentralActor() {
        states.put(CentralClassDeclaration.NAME, new CentralState(new CentralClassDeclaration()));
    }

    public static GlobalState getInstance() {
        return instance;
    }

    public static void setInstance(GlobalState instance) {
        GlobalState.instance = instance;
    }

    public void createMonitoringActors(boolean isStrict) {
        Map<String, MonitoringState> monitoringStates = states
                .keySet()
                .stream()
                .collect(Collectors.toMap(
                        instanceName -> "#monitor_" + instanceName,
                        instanceName -> (isStrict ?
                                new StrictMonitoringState(states.get(instanceName).actor, "#monitor_" + instanceName) :
                                new MonitoringState(states.get(instanceName).actor, "#monitor_" + instanceName)
                        )
                ));
        states.putAll(monitoringStates);
    }

    public Map<String, LocalState> getStates() {
        return states;
    }

    private ReactiveClassDeclaration findReactiveClassDeclaration(Type type, RebecaModel rebecaModel) {
        OrdinaryPrimitiveType ordinaryPrimitiveType = (OrdinaryPrimitiveType) type;
        return rebecaModel
                .getRebecaCode()
                .getReactiveClassDeclaration()
                .stream()
                .filter(it -> it.getName().equals(ordinaryPrimitiveType.getName()))
                .findFirst()
                .orElse(null);
    }

    public Statement getLastStatement(String actorName) {
        LocalState localState = states.get(actorName);
        Statement statement = localState
                .getStatements()
                .stream()
                .findFirst()
                .orElse(null);
        if (statement != null)
            localState.getStatements().remove(0);
        return statement;
    }

    public void addMessageBody(String actorName) {
        states.get(actorName).updateStatements();
    }

    @JsonIgnore
    public Set<String> getInstanceNames() {
        return states.keySet();
    }

    public void setVariableValue(String actorName, String variableName, Value value) {
        states.get(actorName).setVariableValue(variableName, value);
    }

    public void setVariableValue(String actorName, String leftVarName, String rightVarName) {
        states.get(actorName).setVariableValue(leftVarName, rightVarName);
    }

    public void addLocalVariable(String actorName, String varName) {
        states.get(actorName).addLocalVariable(varName);
    }

    public Value getVariableValue(String actorName, String varName) {
        return states.get(actorName).getVariableValue(varName);
    }

    public void insertStatement(Statement statement, String actorName) {
        states.get(actorName).insertStatement(statement);
    }

    public void insertStatements(List<Statement> statements, String actorName) {
        states.get(actorName).insertStatements(statements);
    }

    public String getBinding(String actorName, String calledInstance) {
        return states.get(actorName).getBinding(calledInstance);
    }

    public void addMessageCall(String caller, String callerMethod, String callee, String calleeMethod,
                               List<Value> arguments) {
        Runnable runnable = () -> {
            delayManager.sleep(caller, callerMethod, callee);
            states.get(callee).addMessageCall(calleeMethod, arguments, states.get(caller).getCopyOfVectorClock());
        };
        Thread t = new Thread(runnable);
        t.start();
    }

    public List<String> getCandidateActors() {
        return states.keySet()
                .stream()
                .filter(key -> states.get(key).isExecCandidate())
                .collect(Collectors.toList());
    }

    public void setActorBlockingState(String actorName, boolean isBlocked) {
        getStates().get(actorName).setBlockingState(isBlocked);
    }

    public void addRegularMessageCall(String caller, String callerMethod, String callee, String calleeMethod,
                                      VectorClock vc, RegularMessageType type) {
        Runnable runnable = () -> {
            delayManager.sleep(caller, callerMethod, callee);
            List<Value> arguments = Stream
                    .of(caller, callee, calleeMethod)
                    .map(PrimitiveValue::new)
                    .collect(Collectors.toList());
            getMonitoringState(caller).addSharedMessage(
                    REGULAR_MESSAGE,
                    arguments,
                    vc,
                    type
            );
        };
        Thread t = new Thread(runnable);
        t.start();
    }

    public void addMonitoringMessageCall(String caller, String callerMethod, String callee,
                                         VectorClock vc, MonitoringMessageMeta messageMeta) {
        Runnable runnable = () -> {
            delayManager.sleep(caller, callerMethod, "#monitor_" + callee);
            List<Value> arguments = Collections.singletonList(messageMeta);
            getMonitoringState(callee).addMessageCall(
                    MONITORING_MESSAGE,
                    arguments,
                    vc
            );
        };
        Thread t = new Thread(runnable);
        t.start();
    }

    public void addDeclareMessageCall(String caller, String callerMethod, DeclareMessageMeta messageMeta) {
        List<Value> arguments = Collections.singletonList(messageMeta);
        addMessageCall(caller, callerMethod, CentralClassDeclaration.NAME, DECLARE_METHOD, arguments);
    }

    private MonitoringState getMonitoringState(String instanceName) {
        return (MonitoringState) states.get("#monitor_" + instanceName);
    }

    public void logWaitingMessage(String caller, String calledMethod, String callee, VectorClock vc, long startTime,
                                  long stopTime) {
        getMonitoringState(caller).getMonitoringStateLogger()
                .logWaitingMessage(caller, calledMethod, callee, vc, startTime, stopTime);
    }

    public void logBlockingMessage(String caller, String calledMethod, String callee, VectorClock vc, long startTime,
                                   long stopTime) {
        getMonitoringState(caller).getMonitoringStateLogger()
                .logBlockingMessage(caller, calledMethod, callee, vc, startTime, stopTime);
    }

    public void incrementVectorClock(String instanceName) {
        states.get(instanceName).incrementVectorClock();
    }

    public Map<String, MonitoringState> getMonitors() {
        return states
                .keySet()
                .stream()
                .filter(key -> states.get(key) instanceof MonitoringState)
                .collect(
                        Collectors.toMap(
                                key -> key,
                                key -> (MonitoringState) states.get(key)
                        )
                );
    }

    public boolean isLastMessage(String caller, String callerMethod, String callee) {
        return getMonitoringState(caller).isLastMessage(caller, callee, callerMethod);
    }

    public boolean isLabeledMessage(String caller, String callerMethod, String callee) {
        return getMonitoringState(caller).isLabeledMessage(caller, callee, callerMethod);
    }

    public void getSharedQueuesLock(String caller) {
        getMonitoringState(caller).lockSharedResources();
    }

    public void freeSharedQueuesLock(String caller) {
        getMonitoringState(caller).unlockSharedResources();
    }

    public void addToBlockingQueue(String caller, String callerMethod, String callee) {
        getMonitoringState(caller).addBlockedMessage(caller, callee, callerMethod);
    }

    public void addToErrorMessages(String caller, String methodName, String callee) {
        getMonitoringState(caller).addErrorMessage(caller, callee, methodName);
    }

    public boolean isInBlockingQueue(String caller, String methodName, String callee, BlockedMessageState state) {
        if (state.equals((BlockedMessageState.ERROR)))
            return getMonitoringState(caller)
                    .isBlockedMessageOnStatus(caller, callee, methodName, BlockedMessageState.ERROR);
        else if (state.equals((BlockedMessageState.OK)))
            return getMonitoringState(caller)
                    .isBlockedMessageOnStatus(caller, callee, methodName, BlockedMessageState.OK);
        else return false;
    }

    public boolean isInWaitingQueue(String caller, String methodName, String callee) {
        return getMonitoringState(caller).isWaitedOnMessage(caller, callee, methodName);
    }

    public String getExecutingMethodName(String actorName) {
        return states.get(actorName).getExecutingMethodName();
    }
}
