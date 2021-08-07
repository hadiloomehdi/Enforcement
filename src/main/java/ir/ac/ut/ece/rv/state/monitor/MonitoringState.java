package ir.ac.ut.ece.rv.state.monitor;

import ir.ac.ut.ece.rv.state.*;
import ir.ac.ut.ece.rv.state.central.DeclareMessageMeta;
import ir.ac.ut.ece.rv.state.central.DeclareMessageMetaItem;
import javafx.util.Pair;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.FormalParameterDeclaration;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.MainRebecDefinition;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.MsgsrvDeclaration;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.ReactiveClassDeclaration;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import static ir.ac.ut.ece.rv.state.monitor.MonitoringMsgsrvDeclaration.*;

public class MonitoringState extends LocalState {
    private BlockingQueue<TypedMessageCall> sharedMessages;
    private BlockingQueue<BlockedMessage> blockedMessages;
    private BlockingQueue<Message> waitedMessages;
    private BlockingQueue<Message> errorMessages;
    protected List<History> history;
    private List<MMItem> mmList;
    private List<MMItem> notifyList;
    protected List<History> tempHistory;
    private List<History> vioHistory;
    protected TransitionTable transitionTable;
    private ReactiveClassDeclaration associatedActor;
    protected Map<TransitionClock, List<Transition>> reqTrans;
    private Map<TransitionClock, List<History>> rcvHistory;
    private Map<TransitionClock, Interval> intervals;
    private Map<TransitionClock, Interval> askToTellIntervals;
    private Map<TransitionClock, Interval> tellToNotifyIntervals;
    private Map<TransitionClock, Interval> deadlockToDeadlockIntervals;
    private int latestSize;
    private MonitoringStateLogger monitoringStateLogger;

    public MonitoringState(ReactiveClassDeclaration associatedActor, String instanceName) {
        super(new MonitoringClassDeclaration(instanceName), new MainRebecDefinition());
        sharedMessages = new ArrayBlockingQueue<>(1000);
        blockedMessages = new ArrayBlockingQueue<>(1000);
        waitedMessages = new ArrayBlockingQueue<>(1000);
        errorMessages = new ArrayBlockingQueue<>(1000);
        history = new ArrayList<>();
        mmList = new ArrayList<>();
        notifyList = new ArrayList<>();
        tempHistory = new ArrayList<>();
        vioHistory = new ArrayList<>();
        this.associatedActor = associatedActor;
        transitionTable = new TransitionTable(associatedActor.getName());
        reqTrans = new HashMap<>();
        rcvHistory = new HashMap<>();
        intervals = new HashMap<>();
        askToTellIntervals = new HashMap<>();
        tellToNotifyIntervals = new HashMap<>();
        deadlockToDeadlockIntervals = new HashMap<>();
        latestSize = 0;
        monitoringStateLogger = new MonitoringStateLogger(instanceName);
    }

    public void lockSharedResources() {
        sharedResourcesLock.lock();
    }

    public void unlockSharedResources() {
        sharedResourcesLock.unlock();
    }

    public MonitoringStateLogger getMonitoringStateLogger() {
        return monitoringStateLogger;
    }

    public void addNotifySharedMessage(String methodName, List<Value> argumentValues, VectorClock callerVc) {
        addSharedMessage(methodName, argumentValues, callerVc, RegularMessageType.NOTIFY);
    }

    public void addAskSharedMessage(String methodName, List<Value> argumentValues, VectorClock callerVc) {
        addSharedMessage(methodName, argumentValues, callerVc, RegularMessageType.ASK);
    }

    public void addSharedMessage(String methodName, List<Value> argumentValues, VectorClock callerVc,
                                 RegularMessageType type) {
        sharedResourcesLock.lock();
        List<String> argumentNames = findMethod(methodName)
                .getFormalParameters()
                .stream()
                .map(FormalParameterDeclaration::getName)
                .collect(Collectors.toList());
        sharedMessages.add(
                new TypedMessageCall(methodName, argumentNames, argumentValues, callerVc, type)
        );
        sharedResourcesLock.unlock();
    }

    public void addBlockedMessage(String caller, String callee, String methodName) {
        blockedMessages.add(
                new BlockedMessage(methodName, callee, caller, BlockedMessageState.UNKNOWN)
        );
    }

    public void addWaitedMessage(String caller, String callee, String methodName) {
        waitedMessages.add(
                new Message(methodName, callee, caller)
        );
    }

    public void addErrorMessage(String caller, String callee, String methodName) {
        errorMessages.add(
                new Message(methodName, callee, caller)
        );
    }

    private void sendMonitoringMessage(String callee, MonitoringMessageMeta meta, Transition originTransition) {
        monitoringStateLogger.logSendMonitoringMessage(callee, originTransition, meta.getType());
        VectorClock vc = GlobalState.getInstance().getStates().get("instance" + associatedActor.getName())
                .getCopyOfVectorClock();
        GlobalState.getInstance()
                .addMonitoringMessageCall(instanceName, currentExecutingMethod, callee, vc, meta);
    }

    protected BlockedMessage getBlockedMessage(String caller, String callee, String methodName) {
        Iterator<BlockedMessage> iterator = blockedMessages.iterator();
        BlockedMessage returnMessage = null;
        while (iterator.hasNext()) {
            BlockedMessage message = iterator.next();
            if (message.equal(methodName, callee, caller))
                returnMessage = message;
        }
        return returnMessage;
    }

    private Message getWaitedMessage(String caller, String callee, String methodName) {
        Iterator<Message> iterator = waitedMessages.iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (message.equal(methodName, callee, caller))
                return message;
        }
        return null;
    }

    private Message getErrorMessage(String caller, String callee, String methodName) {
        Iterator<Message> iterator = errorMessages.iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (message.equal(methodName, callee, caller))
                return message;
        }
        return null;
    }

    public boolean isWaitedOnMessage(String caller, String callee, String methodName) {
        Message message = getWaitedMessage(caller, callee, methodName);
        return message != null;
    }

    public Boolean isBlockedMessageOnStatus(String caller, String callee, String methodName,
                                            BlockedMessageState blockedMessageState) {
        BlockedMessage blockedMessage = getBlockedMessage(caller, callee, methodName);
        if (blockedMessage == null)
            return false;
        return blockedMessage.getState() == blockedMessageState;
    }

    public boolean isErrorMessage(String caller, String callee, String methodName) {
        Message message = getErrorMessage(caller, callee, methodName);
        return message != null;
    }

    public boolean isLastMessage(String caller, String callee, String methodName) {
        List<TransitionItem> transitionItems = transitionTable.findItemByMethodName(methodName);
        for (TransitionItem transition : transitionItems) {
            if (transition.getTransition().getCaller().equals(caller) &&
                    transition.getTransition().getCallee().equals(callee) &&
                    transition.isFinal())
                return true;
        }
        return false;
    }

    public boolean isTransitionFinal(Transition transition){
        String methodName = transition.getCalledMethod();
        List<TransitionItem> transitionItems = transitionTable.findItemByMethodName(methodName);
        for (TransitionItem trans : transitionItems) {
            if (trans.getTransition().equals(transition) &&
                    trans.isFinal())
                return true;
        }
        return false;
    }

    public boolean isLabeledMessage(String caller, String callee, String methodName) {
        List<TransitionItem> transitionItems = transitionTable.findItemByMethodName(methodName);
        for (TransitionItem transition :
                transitionItems) {
            if (transition.getTransition().getCaller().equals(caller) &&
                    transition.getTransition().getCallee().equals(callee))
                return true;
        }
        return false;
    }

    @Override
    public boolean isExecCandidate() {
        return super.isExecCandidate() || !sharedMessages.isEmpty();
    }

    @Override
    public void updateStatements() {
        sharedResourcesLock.lock();
        MessageCall message = messages.poll();
        if (message != null) {
            loadMethodBody(message);
        } else {
            message = sharedMessages.poll();
            loadMethodBody(message);
        }
        sharedResourcesLock.unlock();
    }

    @Override
    protected void loadMethodBody(MessageCall messageCall) {
        currentExecutingMethod = messageCall.getMethodName();
        VectorClock messageVectorClock = messageCall.getVectorClock();
        vectorClock.update(messageVectorClock);
        GlobalState.getInstance().getStates().get("instance" + associatedActor.getName())
                .updateVectorClock(messageVectorClock);

        MsgsrvDeclaration methodDeclaration = findMethod(messageCall.getMethodName());
        if (!(methodDeclaration instanceof MonitoringMsgsrvDeclaration))
            throw new RuntimeException("Illegal method '" + messageCall.getMethodName() + "' found in monitor");

        switch (messageCall.getMethodName()) {
            case REGULAR_MESSAGE:
                String caller = messageCall.getPrimitiveArgument("caller");
                String callee = messageCall.getPrimitiveArgument("callee");
                String methodName = messageCall.getPrimitiveArgument("methodName");
                if (messageCall instanceof TypedMessageCall) {
                    TypedMessageCall typedMessageCall = (TypedMessageCall) messageCall;
                    RegularMessageType regularMessageType = typedMessageCall.getType();
                    monitoringStateLogger.logReceiveRegularMessage(caller, callee, methodName, regularMessageType);
                    receiveRegularMessage(caller, callee, methodName, messageVectorClock, regularMessageType);
                } else {
                    System.out.println("Error: Received regular message isn't an instance of TypedMessageCall!");
                }
                break;
            case MONITORING_MESSAGE:
                MonitoringMessageMeta meta = (MonitoringMessageMeta) messageCall.getArgument("meta");
                monitoringStateLogger.logReceiveMonitoringMessage(meta);
                receiveMonitoringMessage(meta);
                break;
            case MAIN_METHOD:
                break;
            default:
                throw new RuntimeException("Illegal method '" + messageCall.getMethodName() + "' found in monitor");
        }

    }

    private void receiveMonitoringMessage(MonitoringMessageMeta meta) {
        MonitoringMessageType type = meta.getType();

        if (type == MonitoringMessageType.ASK) {
            receiveMonitoringMessageAsk(meta);
        } else if (type == MonitoringMessageType.TELL) {
            receiveMonitoringMessageTell(meta);
        } else if (type == MonitoringMessageType.NOTIFY) {
            receiveMonitoringMessageNotify(meta);
        } else if (type == MonitoringMessageType.DEADLOCK) {
            receiveMonitoringMessageDeadLock(meta);
        }
    }

    private void receiveMonitoringMessageNotify(MonitoringMessageMeta meta) {
        Transition originTransition = meta.getOriginTransition();
        String calledMethod = originTransition.getCalledMethod();
        String caller = originTransition.getCaller();
        String callee = originTransition.getCallee();
        TransitionClock key = new TransitionClock(originTransition, meta.getVectorClock());

        if (tellToNotifyIntervals.containsKey(key)) {
            tellToNotifyIntervals.get(key).setEnd(new Date());
        }
        waitedMessages.removeIf(message -> message.equal(calledMethod, callee, caller));
    }


    protected void receiveMonitoringMessageTell(MonitoringMessageMeta meta) {
        Transition originTransition = meta.getOriginTransition();
        Transition targetTransition = meta.getTargetTransition();
        TransitionClock key = new TransitionClock(originTransition, meta.getVectorClock());

        handleNewReceivedMonitoringMessageTell(meta);

        List<Transition> reqTransList = reqTrans.getOrDefault(key, new ArrayList<>());
        reqTransList.remove(targetTransition);
        reqTrans.put(key, reqTransList);

        String originTransitionCaller = originTransition.getCaller();
        String originTransitionCallee = originTransition.getCallee();
        String originTransitionMethodName = originTransition.getCalledMethod();
        boolean isOriginTransitionFinal = isTransitionFinal(originTransition);
        boolean isOriginTransitionBlockedOnUnknown = isBlockedMessageOnStatus(originTransitionCaller,
                originTransitionCallee, originTransitionMethodName, BlockedMessageState.UNKNOWN);
        boolean isFormed = false;

        if (reqTransList.isEmpty() && (isOriginTransitionFinal || !isOriginTransitionBlockedOnUnknown)) {
            if (isErrorMessage(originTransitionCaller, originTransitionCallee, originTransitionMethodName)) {
                Iterator<MMItem> mmItemIterator = notifyList.iterator();
                while (mmItemIterator.hasNext()) {
                    MMItem mmItem = mmItemIterator.next();
                    Transition mmItemTargetTransition = mmItem.getTarget();
                    if (mmItem.getOrigin().equals(originTransition)) {
                        MonitoringMessageMeta newMeta = new MonitoringMessageMeta(
                                MonitoringMessageType.NOTIFY,
                                mmItem.getVc(),
                                originTransition,
                                mmItemTargetTransition,
                                null
                        );
                        sendMonitoringMessage(mmItemTargetTransition.getCaller(), newMeta, originTransition);
                        mmItemIterator.remove();
                    }
                }
                history.removeIf(it -> it.getTransition().equals(originTransition) &&
                        it.getVc().equals(meta.getVectorClock()) &&
                        it.getVc2() == null &&
                        it.getVerdict() == Verdict.UNKNOWN);
                handleMonitorMessage(originTransition);
            } else {
                isFormed = evaluateFormulation(originTransition, meta.getVectorClock());
            }

            BlockedMessage blockedMessage = getBlockedMessage(originTransitionCaller, originTransitionCallee,
                    originTransitionMethodName);
            if (isFormed) {
                assert blockedMessage != null;
                blockedMessage.setState(BlockedMessageState.ERROR);

                List<TransitionClock> emptyReqTransitions = getEmptyReqTransitions(originTransitionMethodName);
                for (TransitionClock emptyReqTransition : emptyReqTransitions) {
                    Transition transition = emptyReqTransition.getTransition();
                    VectorClock transitionVC = emptyReqTransition.getVC();
                    history.removeIf(it -> it.getTransition().equals(transition) &&
                            it.getVc().equals(transitionVC) &&
                            it.getVc2() == null &&
                            it.getVerdict() == Verdict.UNKNOWN);
                    handleMonitorMessage(transition);
                }
            } else if (isOriginTransitionFinal) {
                assert blockedMessage != null;
                blockedMessage.setState(BlockedMessageState.OK);

                List<TransitionClock> emptyReqTransitions = getEmptyReqTransitions(originTransitionMethodName);
                for (TransitionClock emptyReqTransition : emptyReqTransitions) {
                    Transition transition = emptyReqTransition.getTransition();
                    VectorClock transitionVC = emptyReqTransition.getVC();
                    evaluateFormulation(transition, transitionVC);
                }
            }
        }
    }

    protected void handleNewReceivedMonitoringMessageTell(MonitoringMessageMeta meta) {
        Transition originTransition = meta.getOriginTransition();
        Transition targetTransition = meta.getTargetTransition();

        if (meta.getTargetNotify()) {
            notifyList.add(new MMItem(originTransition, targetTransition, meta.getVectorClock(),
                    originTransition.getCaller()));
        }

        TransitionClock key = new TransitionClock(originTransition, meta.getVectorClock());
        askToTellIntervals.get(key).setEnd(new Date());

        List<History> rcvHistList = rcvHistory.getOrDefault(key, new ArrayList<>());
        rcvHistList.addAll(meta.getHistory());
        rcvHistory.put(key, rcvHistList);
    }

    private List<TransitionClock> getEmptyReqTransitions(String methodName) {
        List<TransitionClock> result = new ArrayList<>();
        for (Map.Entry<TransitionClock, List<Transition>> entry : reqTrans.entrySet()) {
            TransitionClock transitionClock = entry.getKey();
            Transition transition = transitionClock.getTransition();
            List<Transition> transitions = entry.getValue();
            if (transition.getCalledMethod().equals(methodName) & transitions.isEmpty()) {
                result.add(transitionClock);
            }
        }
        return result;
    }

    protected boolean evaluateFormulation(Transition originTransition, VectorClock messageVC) {
        TransitionClock key = new TransitionClock(originTransition, messageVC);
        List<History> rcvHistList = rcvHistory.getOrDefault(key, new ArrayList<>());

        transitionTable.getItems().forEach(item -> {
            item.getVio().forEach(vio -> {
                List<History> recs = getRecs(vio, rcvHistList);
                vioHistory.addAll(recs);
                rcvHistList.removeAll(recs);
                rcvHistory.put(key, rcvHistList);
            });
        });
        List<History> recs = rcvHistory.get(key);
        rcvHistory.put(key, new ArrayList<>());
        if (recs!=null){
            if (!recs.isEmpty()){
                recs.forEach(rec -> evalVc(rec, originTransition, messageVC));}}
        boolean isFormed = evalHist(originTransition, messageVC);
        if (recs!=null){
            intervals.get(key).setEnd(new Date());}


        return isFormed;
    }


    private void receiveMonitoringMessageDeadLock(MonitoringMessageMeta meta) {
        Transition metaOriginTransition = meta.getOriginTransition();
        VectorClock messageVC = meta.getVectorClock();
        TransitionClock key = new TransitionClock(metaOriginTransition, messageVC);

        if (deadlockToDeadlockIntervals.containsKey(key)) {
            deadlockToDeadlockIntervals.get(key).setEnd(new Date());
        }

        if (meta.getInitiator().equals(instanceName)) {
            System.out.println("Deadlock is detected");
            Iterator<MMItem> mmListIterator = mmList.iterator();
            while (mmListIterator.hasNext()) {
                MMItem mmListMessage = mmListIterator.next();
                if (mmListMessage.getOrigin().getMethodname().equals(metaOriginTransition.getMethodname()) &&
                        mmListMessage.getSender().equals(metaOriginTransition.getCaller())) {
                    mmListIterator.remove();
                    MonitoringMessageMeta newMessageMeta = new MonitoringMessageMeta(
                            MonitoringMessageType.TELL,
                            mmListMessage.getVc(),
                            mmListMessage.getOrigin(),
                            mmListMessage.getTarget(),
                            getNotUnknownRecs(mmListMessage.getTarget(), mmListMessage.getVc(), history),
                            true,
                            true

                    );
                    sendMonitoringMessage(mmListMessage.getSender(), newMessageMeta, mmListMessage.getOrigin());
                    TransitionClock key = new TransitionClock(metaOriginTransition, mmListMessage.getVc());
                    tellToNotifyIntervals.put(key, new Interval(new Date()));

                }
            }
        } else {
            for (Transition metaTargetTransition : meta.getTargetTransitions()) {
                String calledMethod = metaTargetTransition.getCalledMethod();
                if (isBlockedMessageOnStatus(metaTargetTransition.getCaller(), metaTargetTransition.getCallee(),
                        calledMethod, BlockedMessageState.UNKNOWN)) {
                    ArrayList<Transition> presAndVios = new ArrayList<>();
                    for (TransitionItem transitionItem : transitionTable.findItemByMethodName(calledMethod))
                        if (transitionItem.isFinal()) {
                            if (transitionItem.isPreind())
                                presAndVios.add(transitionItem.getPre());
                            presAndVios.addAll(transitionItem.getVoiIndTransitions());
                        }
                    Iterator<Transition> iterator = presAndVios.iterator();
                    while (iterator.hasNext()) {
                        Transition transition = iterator.next();
                        Set<Pair<String, String>> route = new HashSet<>(meta.getRoute());
                        Set<Transition> monitoringMsgTargetTransitions = new HashSet<>();
                        monitoringMsgTargetTransitions.add(transition);
                        route.add(new Pair<>(calledMethod, transition.getCalledMethod()));
                        Iterator<Transition> nextIterator = presAndVios.iterator();
                        while (nextIterator.hasNext()) {
                            Transition innerTransition = nextIterator.next();
                            if (innerTransition != transition &&
                                    innerTransition.getCaller().equals(transition.getCaller())) {
                                route.add(new Pair<>(calledMethod, innerTransition.getCalledMethod()));
                                monitoringMsgTargetTransitions.add(innerTransition);
                                nextIterator.remove();
                            }
                        }
                        MonitoringMessageMeta messageMeta = new MonitoringMessageMeta(
                                MonitoringMessageType.DEADLOCK,
                                meta.getVectorClock(),
                                metaTargetTransition,
                                monitoringMsgTargetTransitions,
                                null,
                                false,
                                false,
                                meta.getInitiator(),
                                route
                        );
                        sendMonitoringMessage(transition.getCaller(), messageMeta, metaOriginTransition);

                        intervals.put(key, new Interval(new Date()));
                        deadlockToDeadlockIntervals.put(key, new Interval(new Date()));
                    }
                }
            }
        }

    }

    protected void receiveMonitoringMessageAsk(MonitoringMessageMeta meta) {
        Set<Transition> targetTransitions = meta.getTargetTransitions();

        for (Transition targetTransition : targetTransitions) {
            String calledMethod = targetTransition.getCalledMethod();
            String callee = targetTransition.getCallee();
            String caller = targetTransition.getCaller();
            boolean isTargetTransitionBlocked = isBlockedMessageOnStatus(caller, callee, calledMethod,
                    BlockedMessageState.UNKNOWN);

            if (meta.isBlocked()) {
                if (isTargetTransitionBlocked) {
                    handleMonitoringBlockedTargetBlockedAsk(meta, targetTransition);
                } else {
                    handleMonitoringBlockedTargetNotBlockedAsk(meta, targetTransition);
                }
            } else {
                if (isTargetTransitionBlocked) {
                    handleMonitoringNotBlockedTargetBlockedAsk(meta, targetTransition);
                } else {
                    handleMonitoringNotBlockedTargetNotBlockedAsk(meta, targetTransition);
                }
            }
        }
    }

    protected void handleMonitoringBlockedTargetBlockedAsk(MonitoringMessageMeta meta, Transition targetTransition) {
        String calledMethod = targetTransition.getCalledMethod();

        List<TransitionItem> finalInitTransitions = transitionTable.findItemByMethodName(calledMethod)
                .stream()
                .filter(TransitionItem::isFinal)
                .filter(TransitionItem::isInit)
                .collect(Collectors.toList());
        if (finalInitTransitions.size() > 0) {
            ArrayList<Transition> presAndVios = new ArrayList<>();
            for (TransitionItem transitionItem : finalInitTransitions) {
                if (transitionItem.isPreind()) {
                    presAndVios.add(transitionItem.getPre());
                }
                presAndVios.addAll(transitionItem.getVoiIndTransitions());
            }
            Iterator<Transition> iterator = presAndVios.iterator();
            while (iterator.hasNext()) {
                Transition transition = iterator.next();
                Set<Pair<String, String>> route = new HashSet<>();
                Set<Transition> monitoringMsgTargetTransitions = new HashSet<>();
                monitoringMsgTargetTransitions.add(transition);
                route.add(new Pair<>(calledMethod, transition.getCalledMethod()));
                Iterator<Transition> nextIterator = presAndVios.iterator();
                while (nextIterator.hasNext()) {
                    Transition innerTransition = nextIterator.next();
                    if (innerTransition != transition && innerTransition.getCaller().equals(transition.getCaller())) {
                        route.add(new Pair<>(calledMethod, innerTransition.getCalledMethod()));
                        monitoringMsgTargetTransitions.add(innerTransition);
                        nextIterator.remove();
                    }
                }
                MonitoringMessageMeta messageMeta = new MonitoringMessageMeta(
                        MonitoringMessageType.DEADLOCK,
                        meta.getVectorClock(),
                        targetTransition,
                        monitoringMsgTargetTransitions,
                        null,
                        false,
                        false,
                        instanceName,
                        route
                );
                sendMonitoringMessage(transition.getCaller(), messageMeta, meta.getOriginTransition());

                Transition metaOriginTransition = meta.getOriginTransition();
                VectorClock messageVC = meta.getVectorClock();
                TransitionClock key = new TransitionClock(metaOriginTransition, messageVC);
                intervals.put(key, new Interval(new Date()));
                deadlockToDeadlockIntervals.put(key, new Interval(new Date()));
            }
        }
        mmList.add(new MMItem(meta.getOriginTransition(), targetTransition, meta.getVectorClock(),
                meta.getOriginTransition().getCaller()));
    }

    protected void handleMonitoringBlockedTargetNotBlockedAsk(MonitoringMessageMeta meta, Transition targetTransition) {
        Transition originTransition = meta.getOriginTransition();
        List<History> recs = getRecs(targetTransition, meta.getVectorClock(), history);

        if (hasUnknownVerdict(targetTransition, meta.getVectorClock(), this.history) ||
                hasUnhandledMsg(targetTransition, meta.getVectorClock())) {
            mmList.add(new MMItem(originTransition, targetTransition, meta.getVectorClock(),
                    originTransition.getCaller()));
        } else {
            if (recs.size() == 0) {
                addWaitedMessage(meta.getOriginTransition().getCaller(), meta.getOriginTransition().getCallee(),
                        meta.getOriginTransition().getCalledMethod());
            }

            Boolean targetNotify = (recs.size() == 0);
            VectorClock messageVC = meta.getVectorClock();
            MonitoringMessageMeta newMeta = new MonitoringMessageMeta(
                    MonitoringMessageType.TELL,
                    messageVC,
                    originTransition,
                    targetTransition,
                    recs,
                    false,
                    targetNotify
            );
            sendMonitoringMessage(originTransition.getCaller(), newMeta, originTransition);
            if (targetNotify) {
                TransitionClock key = new TransitionClock(originTransition, messageVC);
                tellToNotifyIntervals.put(key, new Interval(new Date()));
            }
        }
    }


    private void handleMonitoringNotBlockedTargetBlockedAsk(MonitoringMessageMeta meta, Transition targetTransition) {
        List<History> notUnknownRecs = getNotUnknownRecs(targetTransition, meta.getVectorClock(), history);
        MonitoringMessageMeta newMeta = new MonitoringMessageMeta(
                MonitoringMessageType.TELL,
                meta.getVectorClock(),
                meta.getOriginTransition(),
                targetTransition,
                notUnknownRecs
        );
        sendMonitoringMessage(meta.getOriginTransition().getCaller(), newMeta, meta.getOriginTransition());
    }

    private void handleMonitoringNotBlockedTargetNotBlockedAsk(MonitoringMessageMeta meta,
                                                               Transition targetTransition) {
        Transition originTransition = meta.getOriginTransition();

        if (hasUnknownVerdict(targetTransition, meta.getVectorClock(), this.history) ||
                hasUnhandledMsg(targetTransition, meta.getVectorClock())) {
            mmList.add(new MMItem(originTransition, targetTransition, meta.getVectorClock(),
                    originTransition.getCaller()));
        } else {
            List<History> notUnknownRecs = getNotUnknownRecs(targetTransition, meta.getVectorClock(), history);
            MonitoringMessageMeta newMeta = new MonitoringMessageMeta(
                    MonitoringMessageType.TELL,
                    meta.getVectorClock(),
                    originTransition,
                    targetTransition,
                    notUnknownRecs
            );
            sendMonitoringMessage(originTransition.getCaller(), newMeta, originTransition);
        }
    }

    protected boolean evalHist(Transition t, VectorClock vc) {
        boolean flag = false;

        if (tempHistory.stream().anyMatch(it -> it.getTransition().equals(t))) {
            List<History> recs = getRecs(t, tempHistory);
            VectorClock vc2 = recs.stream().findFirst().get().getVc2();
            for (History rec : recs) {
                vc2 = VectorClock.of(vc2, rec.getVc2());
            }
            if (tempHistory.stream().anyMatch(
                    it -> it.getTransition().equals(t) && it.getVc().equals(vc) && it.getVerdict() == Verdict.FALSE)) {
                updateHistory(t, vc, vc2, Verdict.FALSE);
            } else {
                updateHistory(t, vc, vc2, Verdict.BOTH);
            }
            if (reachToFinalState(t) && (getVerdict(t) == Verdict.FALSE || getVerdict(t) == Verdict.BOTH)) {
                flag = true;
                declare(getVerdict(t));
            }

            tempHistory.removeAll(recs);
        } else {
            history.removeIf(it -> it.getTransition().equals(t) && it.getVc().equals(vc) && it.getVc2() == null &&
                    it.getVerdict() == Verdict.UNKNOWN);
        }
        handleMonitorMessage(t);

        return flag;
    }

    protected void handleMonitorMessage(Transition t) {
        List<MMItem> handleList = mmList
                .stream()
                .filter(it -> it.getTarget().equals(t))
                .filter(it -> !hasUnknownVerdict(it.getTarget(), it.getVc(), history))
                .filter(it -> !hasUnhandledMsg(it.getTarget(), it.getVc()))
                .collect(Collectors.toList());

        for (MMItem mmItem : handleList) {
            List<History> recs = getRecs(mmItem.getTarget(), mmItem.getVc(), history);
            MonitoringMessageMeta meta = new MonitoringMessageMeta(
                    MonitoringMessageType.TELL,
                    mmItem.getVc(),
                    mmItem.getOrigin(),
                    mmItem.getTarget(),
                    recs
            );
            sendMonitoringMessage(mmItem.getOrigin().getCaller(), meta, mmItem.getOrigin());
            mmList.remove(mmItem);
        }
    }

    protected void declare(Verdict verdict) {
        List<DeclareMessageMetaItem> items = new ArrayList<>();
        GlobalState
                .getInstance()
                .getMonitors()
                .forEach((key, monitor) -> items.add(new DeclareMessageMetaItem(key, monitor.history.size())));

        DeclareMessageMeta meta = new DeclareMessageMeta(verdict, items);
        GlobalState
                .getInstance()
                .addDeclareMessageCall(instanceName, currentExecutingMethod, meta);
        System.out.println("declare sent to central monitor");
    }

    protected Verdict getVerdict(Transition t) {
        return tempHistory
                .stream()
                .filter(it -> it.getTransition().equals(t))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No record in temp history with given 't'"))
                .getVerdict();
    }

    private boolean reachToFinalState(Transition t) {
        return transitionTable
                .getItems()
                .stream()
                .anyMatch(it -> it.getTransition().equals(t) && it.isFinal());
    }

    protected void updateHistory(Transition t, VectorClock vc, VectorClock vc2, Verdict verdict) {
        List<History> pc = history
                .stream()
                .filter(it -> it.getTransition().equals(t) && it.getVc().equals(vc) &&
                        it.getVerdict() == Verdict.UNKNOWN)
                .collect(Collectors.toList());
        for (History it : pc) {
            it.setVc2(vc2);
            it.setVerdict(verdict);
        }
    }

    private void evalVc(History r, Transition t, VectorClock vc) {
        VectorClock vc1 = r.getVc();
        VectorClock vc2 = r.getVc2();
        Verdict verdict = r.getVerdict();
        Transition pre = r.getTransition();

        Verdict status = notViolation(pre, vc1, vc2);
        if (status == Verdict.TRUE) {
            if (vc1.isConcurrent(vc)) {
                tempHistory.add(new History(t, vc, VectorClock.of(vc, vc2), Verdict.BOTH));
            } else if (vc1.isLessThan(vc)) {
                if (verdict == Verdict.TRUE) {
                    tempHistory.add(new History(t, vc, VectorClock.of(vc, vc2), Verdict.FALSE));
                } else {
                    tempHistory.add(new History(t, vc, VectorClock.of(vc, vc2), verdict));
                }
            }
        } else if (status == Verdict.BOTH) {
            if (!vc.isLessThan(vc1)) {
                tempHistory.add(new History(t, vc, VectorClock.of(vc, vc2), Verdict.BOTH));
            }
        }
    }

    private Verdict notViolation(Transition pre, VectorClock vc1Pre, VectorClock vc2Pre) {
        Verdict status = Verdict.TRUE;
        List<TransitionItem> items = transitionTable
                .getItems()
                .stream()
                .filter(it -> pre.equals(it.getPre()))
                .collect(Collectors.toList());
        for (TransitionItem it : items) {
            for (Transition vio : it.getVio()) {
                List<History> recs = getRecs(vio, vioHistory);
                for (History rec : recs) {
                    VectorClock vc1Vio = rec.getVc();
                    VectorClock vc2Vio = rec.getVc2();
                    if (vc1Pre.isLessThan(vc1Vio)) {
                        return Verdict.FALSE;
                    } else if (vc1Vio.isConcurrent(vc1Pre) && vc2Pre.isLessThan(vc2Vio)) {
                        status = Verdict.BOTH;
                    }
                }
            }
        }
        return status;
    }

    protected List<History> getRecs(Transition transition, List<History> list) {
        return list
                .stream()
                .filter(it -> it.getTransition().equals(transition))
                .collect(Collectors.toList());
    }

    private List<History> getRecs(Transition transition, VectorClock vc, List<History> list) {
        return list
                .stream()
                .filter(it -> it.getTransition().equals(transition) &&
                        (it.getVc().isLessThan(vc) || it.getVc().isConcurrent(vc)))
                .collect(Collectors.toList());
    }

    private List<History> getNotUnknownRecs(Transition transition, VectorClock vc, List<History> list) {
        return list
                .stream()
                .filter(it -> it.getTransition().equals(transition) &&
                        (it.getVc().isLessThan(vc) || it.getVc().isConcurrent(vc)) &&
                        it.getVerdict() != Verdict.UNKNOWN)
                .collect(Collectors.toList());
    }

    private boolean hasUnhandledMsg(Transition targetTransition, VectorClock vc) {
        sharedResourcesLock.lock();
        try {
            return sharedMessages.stream().anyMatch(it -> {
                String methodName = it.getPrimitiveArgument("methodName");
                return methodName.equals(targetTransition.getCalledMethod()) &&
                        ((it.getVectorClock().isLessThan(vc) || it.getVectorClock().isConcurrent(vc)));
            });
        } finally {
            sharedResourcesLock.unlock();
        }
    }

    private boolean hasUnknownVerdict(Transition targetTransition, VectorClock vc, List<History> history) {
        return history.stream().anyMatch(it ->
                it.getVerdict() == Verdict.UNKNOWN &&
                        (it.getTransition().equals(targetTransition)) &&
                        (it.getVc().isLessThan(vc) || it.getVc().isConcurrent(vc))
        );
    }

    private void receiveRegularMessage(String caller, String callee, String methodName, VectorClock messageVC,
                                       RegularMessageType type) {
        if (type == RegularMessageType.ASK)
            receiveRegularMessageAsk(caller, callee, methodName, messageVC);
        else if (type == RegularMessageType.NOTIFY)
            receiveRegularMessageNotify(methodName, messageVC);
    }

    protected void receiveRegularMessageAsk(String caller, String callee, String methodName,
                                            VectorClock messageVC) {
        List<TransitionItem> transitionItems = transitionTable.findItemByMethodName(methodName);
        boolean isLastMessage = isLastMessage(caller, callee, methodName);

        for (TransitionItem transitionItem : transitionItems) {
            handleTransitionItemAsk(transitionItem, isLastMessage, messageVC);
        }
    }

    protected void handleTransitionItemAsk(TransitionItem transitionItem, Boolean isLastMessage,
                                           VectorClock messageVC) {
        List<Transition> presAndVios = getPresAndVios(transitionItem);
        Transition origin = transitionItem.getTransition();
        handleReqTransAsk(origin, presAndVios, messageVC);
        handlePreAndViosAsk(origin, presAndVios, isLastMessage, messageVC);
    }

    private void handleReqTransAsk(Transition origin, List<Transition> presAndVios, VectorClock messageVC) {
        TransitionClock key = new TransitionClock(origin, messageVC);
        List<Transition> reqTransitions = reqTrans.getOrDefault(key, new ArrayList<>());
        reqTransitions.addAll(presAndVios);
        reqTrans.put(key, reqTransitions);
    }

    private List<Transition> getPresAndVios(TransitionItem transitionItem) {
        List<Transition> presAndVios = new ArrayList<>();
        if (transitionItem.getPre() != null) {
            presAndVios.add(transitionItem.getPre());
        }
        presAndVios.addAll(transitionItem.getVio());
        return presAndVios;
    }

    private void handlePreAndViosAsk(Transition origin, List<Transition> presAndVios, Boolean isLastMessage,
                                     VectorClock messageVC) {
        if (presAndVios.size() > 0) {
            TransitionClock key = new TransitionClock(origin, messageVC);

            presAndVios.forEach(target -> {
                Set<Transition> transProcess = new HashSet<>();
                transProcess.add(target);

                Iterator<Transition> transitionIterator = presAndVios.iterator();
                while (transitionIterator.hasNext()) {
                    Transition transition = transitionIterator.next();
                    if (transition.getCaller().equals(target.getCaller()) && !transition.equals(target)) {
                        transProcess.add(transition);
                        transitionIterator.remove();
                    }
                }

                MonitoringMessageMeta meta = new MonitoringMessageMeta(
                        MonitoringMessageType.ASK,
                        messageVC,
                        origin,
                        transProcess,
                        Collections.emptyList(),
                        isLastMessage,
                        false
                );

                sendMonitoringMessage(target.getCaller(), meta, origin);
                intervals.put(key, new Interval(new Date()));
                askToTellIntervals.put(key, new Interval(new Date()));
            });

            if (!hasUnknownVerdict(origin, messageVC, history)) {
                history.add(new History(origin, messageVC, null, Verdict.UNKNOWN));
            }
        } else {
            if (!isLastMessage) {
                history.add(new History(origin, messageVC, messageVC, Verdict.TRUE));
            }
            handleMonitorMessage(origin);
        }
    }

    private void receiveRegularMessageNotify(String methodName, VectorClock messageVC) {
        Iterator<MMItem> mmItemIterator = notifyList.iterator();
        while (mmItemIterator.hasNext()) {
            MMItem mmItem = mmItemIterator.next();
            if (mmItem.getOrigin().getCalledMethod().equals(methodName)) {
                Transition origin = mmItem.getOrigin();
                Transition target = mmItem.getTarget();

                MonitoringMessageMeta meta = new MonitoringMessageMeta(
                        MonitoringMessageType.NOTIFY,
                        mmItem.getVc(),
                        origin,
                        target,
                        null
                );
                sendMonitoringMessage(target.getCaller(), meta, origin);
                mmItemIterator.remove();
            }
        }
    }

    public Map<TransitionClock, Interval> getIntervals() {
        return intervals;
    }

    public Map<TransitionClock, Interval> getAskToTellIntervals() {
        return askToTellIntervals;
    }

    public Map<TransitionClock, Interval> getTellToNotifyIntervals() {
        return tellToNotifyIntervals;
    }

    public Map<TransitionClock, Interval> getDeadlockToDeadlockIntervals() {
        return deadlockToDeadlockIntervals;
    }

    public int getTotalMonitoringSent(MonitoringMessageType monitoringMessageType) {
        return monitoringStateLogger.getTotalMonitoringSent(monitoringMessageType);
    }

    public int getTotalMonitoringReceived(MonitoringMessageType monitoringMessageType) {
        return monitoringStateLogger.getTotalMonitoringReceived(monitoringMessageType);
    }

    public int getTotalWaitingTime() {
        return monitoringStateLogger.getTotalWaitTime();
    }

    public int getTotalBlockingTime() {
        return monitoringStateLogger.getTotalBlockTime();
    }

    public List<History> getHistory() {
        return history;
    }

    public List<MMItem> getMmList() {
        return mmList;
    }

    public List<History> getTempHistory() {
        return tempHistory;
    }

    public List<History> getVioHistory() {
        return vioHistory;
    }

    public int getLatestSize() {
        return latestSize;
    }

    public void setLatestSize(int latestSize) {
        this.latestSize = latestSize;
    }
}
