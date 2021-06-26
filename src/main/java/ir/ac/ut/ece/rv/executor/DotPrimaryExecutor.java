package ir.ac.ut.ece.rv.executor;

import ir.ac.ut.ece.rv.state.GlobalState;
import ir.ac.ut.ece.rv.state.Value;
import ir.ac.ut.ece.rv.state.VectorClock;
import ir.ac.ut.ece.rv.state.monitor.BlockedMessageState;
import ir.ac.ut.ece.rv.state.monitor.RegularMessageType;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.DotPrimary;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.TermPrimary;

import java.util.List;
import java.util.stream.Collectors;

public class DotPrimaryExecutor extends StatementExecutor<DotPrimary> {

    public boolean needBlocking(GlobalState globalState,String actorName,String calledMethod,String calledActor){
        return globalState.isLastMessage(actorName, calledMethod, calledActor);
    }

    @Override
    public GlobalState exec(DotPrimary statement, GlobalState globalState, String actorName) {
        TermPrimary leftTerm = (TermPrimary) statement.getLeft();
        TermPrimary rightTerm = (TermPrimary) statement.getRight();
        String calledInstance = leftTerm.getName();
        String calledMethod = rightTerm.getName();

        String calledActor;
        if (calledInstance.equals("self")) {
            calledActor = actorName;
        } else {
            calledActor = globalState.getBinding(actorName, calledInstance);
        }
        String executingMethodName = globalState.getExecutingMethodName(actorName);
        List<Value> arguments = rightTerm
                .getParentSuffixPrimary()
                .getArguments()
                .stream()
                .map(argument -> ExpressionEvaluator.evaluate(argument, globalState, actorName))
                .collect(Collectors.toList());
        globalState.setActorBlockingState(actorName, true);
        Thread thread = new Thread(() -> {
            if (needBlocking(globalState, actorName, calledMethod, calledActor)) {
                VectorClock vc = globalState.getStates().get(actorName).getCopyOfVectorClock();
                globalState.addRegularMessageCall(actorName, executingMethodName, calledActor, calledMethod, vc, RegularMessageType.ASK);
                globalState.addToBlockingQueue(actorName, calledMethod, calledActor);
                long startTime = System.currentTimeMillis();
                while (true) {
                    if (globalState.isInBlockingQueue(actorName, calledMethod, calledActor, BlockedMessageState.ERROR)) {
                        long stopTime = System.currentTimeMillis();
                        globalState.getSharedQueuesLock(actorName);
//                        TODO: send error to central monitor
                        globalState.addToErrorMessages(actorName, calledMethod, calledActor);
                        System.err.println("ERROR");
                        globalState.addRegularMessageCall(actorName, executingMethodName, calledActor, calledMethod, vc, RegularMessageType.NOTIFY);
                        globalState.logBlockingMessage(actorName, calledMethod, calledActor, vc, startTime, stopTime);
                        globalState.freeSharedQueuesLock(actorName);
                        break;
                    }
                    else if (globalState.isInBlockingQueue(actorName, calledMethod, calledActor, BlockedMessageState.OK)) {
                        long stopTime = System.currentTimeMillis();
                        globalState.getSharedQueuesLock(actorName);
                        globalState.addMessageCall(actorName, executingMethodName, calledActor, calledMethod, arguments);
                        globalState.addRegularMessageCall(actorName, executingMethodName, calledActor, calledMethod, vc, RegularMessageType.NOTIFY);
                        globalState.logBlockingMessage(actorName, calledMethod, calledActor, vc, startTime, stopTime);
                        globalState.freeSharedQueuesLock(actorName);
                        break;
                    }
                }
            } else {
                long startTime = System.currentTimeMillis();
                while (true){
                    globalState.getSharedQueuesLock(actorName);
                    if (!globalState.isInWaitingQueue(actorName, calledMethod, calledActor)) {
                        VectorClock vc = globalState.getStates().get(actorName).getCopyOfVectorClock();
                        long stopTime = System.currentTimeMillis();
                        globalState.addMessageCall(actorName, executingMethodName, calledActor, calledMethod, arguments);
                        globalState.addRegularMessageCall(actorName, executingMethodName, calledActor, calledMethod, vc, RegularMessageType.ASK);
                        globalState.logWaitingMessage(actorName, calledMethod, calledActor, vc, startTime, stopTime);
                        globalState.freeSharedQueuesLock(actorName);
                        break;
                    }
                    globalState.freeSharedQueuesLock(actorName);
                }
            }
            globalState.setActorBlockingState(actorName, false);
        });
        thread.start();
        return globalState;
    }
}
