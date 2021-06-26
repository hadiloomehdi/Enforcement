package ir.ac.ut.ece.rv.state.monitor;

import ir.ac.ut.ece.rv.state.Value;
import ir.ac.ut.ece.rv.state.VectorClock;
import javafx.util.Pair;

import java.util.List;
import java.util.Set;

public class MonitoringMessageMeta extends Value {
    private MonitoringMessageType type;
    private VectorClock vectorClock;
    private Transition originTransition;
    private Transition targetTransition;
    private Set<Transition> targetTransitions;
    private List<History> history;
    private boolean isBlocked;
    private boolean targetNotify;
    private String initiator;
    private Set<Pair<String,String>> route;

    public MonitoringMessageMeta(
            MonitoringMessageType type,
            VectorClock vectorClock,
            Transition originTransition,
            Transition targetTransition,
            List<History> history
    ) {
        this.type = type;
        this.vectorClock = vectorClock;
        this.originTransition = originTransition;
        this.targetTransition = targetTransition;
        this.history = history;
    }

    public MonitoringMessageMeta(
            MonitoringMessageType type,
            VectorClock vectorClock,
            Transition originTransition,
            Transition targetTransition,
            List<History> history,
            Boolean isBlocked,
            Boolean targetNotify
    ) {
        this.type = type;
        this.vectorClock = vectorClock;
        this.originTransition = originTransition;
        this.targetTransition = targetTransition;
        this.history = history;
        this.isBlocked = isBlocked;
        this.targetNotify = targetNotify;
    }

    public MonitoringMessageMeta(
            MonitoringMessageType type,
            VectorClock vectorClock,
            Transition originTransition,
            Set<Transition> targetTransitions,
            List<History> history,
            Boolean isBlocked,
            Boolean targetNotify
    ) {
        this.type = type;
        this.vectorClock = vectorClock;
        this.originTransition = originTransition;
        this.targetTransitions = targetTransitions;
        this.history = history;
        this.isBlocked = isBlocked;
        this.targetNotify = targetNotify;
    }

    public MonitoringMessageMeta(
            MonitoringMessageType type,
            VectorClock vectorClock,
            Transition originTransition,
            Set<Transition> targetTransitions,
            List<History> history,
            Boolean isBlocked,
            Boolean targetNotify,
            String initiator,
            Set<Pair<String,String>> route
    ) {
        this.type = type;
        this.vectorClock = vectorClock;
        this.originTransition = originTransition;
        this.targetTransitions = targetTransitions;
        this.history = history;
        this.isBlocked = isBlocked;
        this.targetNotify = targetNotify;
        this.initiator = initiator;
        this.route = route;
    }
    public boolean getTargetNotify() { return targetNotify; }

    public MonitoringMessageType getType() {
        return type;
    }

    public VectorClock getVectorClock() {
        return vectorClock;
    }

    public Transition getOriginTransition() {
        return originTransition;
    }

    public Transition getTargetTransition() {
        return targetTransition;
    }

    public Set<Transition> getTargetTransitions() {
        return targetTransitions;
    }

    public String getInitiator() {
        return initiator;
    }

    public Set<Pair<String, String>> getRoute() {
        return route;
    }

    public List<History> getHistory() {
        return history;
    }

    public boolean isBlocked() {
        return isBlocked;
    }
}


