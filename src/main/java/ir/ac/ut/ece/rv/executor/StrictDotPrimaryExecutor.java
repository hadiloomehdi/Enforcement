package ir.ac.ut.ece.rv.executor;

import ir.ac.ut.ece.rv.state.GlobalState;

public class StrictDotPrimaryExecutor extends DotPrimaryExecutor {

    @Override
    public boolean needBlocking(GlobalState globalState, String actorName, String calledMethod, String calledActor) {
        return globalState.isLabeledMessage(actorName, calledMethod, calledActor);
    }
}
