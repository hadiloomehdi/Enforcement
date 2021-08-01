package ir.ac.ut.ece.rv.state.monitor;

import ir.ac.ut.ece.rv.state.VectorClock;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.ReactiveClassDeclaration;

import java.util.*;
import java.util.stream.Collectors;

public class StrictMonitoringState extends MonitoringState {
    private Map<String, Set<Transition>> transOfMessages;

    public StrictMonitoringState(ReactiveClassDeclaration associatedActor, String instanceName) {
        super(associatedActor, instanceName);
        transOfMessages = new HashMap<>();
    }

    @Override
    protected void receiveRegularMessageAsk(String caller, String callee, String methodName, VectorClock messageVC) {
        List<TransitionItem> transitionItems = transitionTable.findItemByMethodName(methodName);
        boolean isLastMessage = isLastMessage(caller, callee, methodName);
        Set<Transition> transOfMessage = transOfMessages.getOrDefault(methodName, new HashSet<>());
        boolean noPre = true;

        for (TransitionItem transitionItem : transitionItems) {
            if (transitionItem.getPre() != null) {
                noPre = false;
                transOfMessage.add(transitionItem.getTransition());
            }
            handleTransitionItemAsk(transitionItem, isLastMessage, messageVC);
        }
        transOfMessages.put(methodName, transOfMessage);

        if (noPre) {
            BlockedMessage blockedMessage = getBlockedMessage(caller, callee, methodName);
            if (blockedMessage != null) {
                blockedMessage.setState(BlockedMessageState.OK);
            }
        }
    }

    @Override
    protected void receiveMonitoringMessageAsk(MonitoringMessageMeta meta) {
        Set<Transition> targetTransitions = meta.getTargetTransitions();

        for (Transition targetTransition : targetTransitions) {
            String calledMethod = targetTransition.getCalledMethod();
            String callee = targetTransition.getCallee();
            String caller = targetTransition.getCaller();
            boolean isTargetTransitionBlocked = isBlockedMessageOnStatus(caller, callee, calledMethod,
                    BlockedMessageState.UNKNOWN);

            if (isTargetTransitionBlocked) {
                handleMonitoringBlockedTargetBlockedAsk(meta, targetTransition);
            } else {
                handleMonitoringBlockedTargetNotBlockedAsk(meta, targetTransition);
            }
        }
    }

    @Override
    protected void receiveMonitoringMessageTell(MonitoringMessageMeta meta) {
        Transition originTransition = meta.getOriginTransition();
        Transition targetTransition = meta.getTargetTransition();
        TransitionClock key = new TransitionClock(originTransition, meta.getVectorClock());

        handleNewReceivedMonitoringMessageTell(meta);

        List<Transition> reqTransList = reqTrans.getOrDefault(key, new ArrayList<>());
        reqTransList.remove(targetTransition);
        reqTrans.put(key, reqTransList);

        String originTransitionMethodName = originTransition.getCalledMethod();

        Set<Transition> transOfMessage = transOfMessages.getOrDefault(originTransitionMethodName, new HashSet<>());
        transOfMessage.remove(originTransition);
        transOfMessages.put(originTransitionMethodName, transOfMessage);

        if (reqTransList.isEmpty()) {
            boolean isFormed = evaluateFormulation(originTransition, meta.getVectorClock());

            if (transOfMessage.isEmpty()) {
                List<TransitionClock> originTransitionClocks = getReqTransTransitionClocks(originTransitionMethodName);
                String originTransitionCaller = originTransition.getCaller();
                String originTransitionCallee = originTransition.getCallee();
                BlockedMessage blockedMessage = getBlockedMessage(originTransitionCaller, originTransitionCallee,
                        originTransitionMethodName);
                assert blockedMessage != null;

                if (!isLastMessage(originTransitionCaller, originTransitionCallee, originTransitionMethodName)) {
                    blockedMessage.setState(BlockedMessageState.OK);
                } else {
                    if (!isFormed) {
                        blockedMessage.setState(BlockedMessageState.OK);
                    } else {
                        blockedMessage.setState(BlockedMessageState.ERROR);
                        for (TransitionClock transitionClock : originTransitionClocks) {
                            history.removeIf(it -> it.getTransition().equals(transitionClock.getTransition()) &&
                                    it.getVc().equals(transitionClock.getVC()));
                        }
                    }
                }
                handleMonitorMessage(originTransition);
                for (TransitionClock transitionClock : originTransitionClocks) {
                    handleMonitorMessage(transitionClock.getTransition());
                }
            }
        }
    }

    @Override
    protected boolean evalHist(Transition t, VectorClock vc) {
        boolean flag = false;

        if (tempHistory.stream().anyMatch(it -> it.getTransition().equals(t))) {
            List<History> recs = getRecs(t, tempHistory);
            VectorClock vc2 = recs.stream().findFirst().get().getVc2();
            for (History rec : recs) {
                vc2 = VectorClock.of(vc2, rec.getVc2());
            }
            if (tempHistory.stream().anyMatch(it -> it.getTransition().equals(t) && it.getVc().equals(vc)
                    && it.getVerdict() == Verdict.FALSE)) {
                updateHistory(t, vc, vc2, Verdict.FALSE);
            } else {
                updateHistory(t, vc, vc2, Verdict.BOTH);
            }
            if (getVerdict(t) == Verdict.FALSE || getVerdict(t) == Verdict.BOTH) {
                flag = true;
                declare(getVerdict(t));
            }

            tempHistory.removeAll(recs);
        } else {
            history.removeIf(it -> it.getTransition().equals(t) && it.getVc().equals(vc) && it.getVc2() == null
                    && it.getVerdict() == Verdict.UNKNOWN);
        }
        handleMonitorMessage(t);

        return flag;
    }

    private List<TransitionClock> getReqTransTransitionClocks(String methodName) {
        return reqTrans
                .keySet()
                .stream()
                .filter(it -> it.getTransition().getCalledMethod().equals(methodName))
                .collect(Collectors.toList());
    }
}
