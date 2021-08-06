package ir.ac.ut.ece.rv.state.monitor;

import ir.ac.ut.ece.rv.state.VectorClock;

import java.util.EnumMap;
import java.util.Map;

public class MonitoringStateLogger {
    private String instanceName;
    private Map<MonitoringMessageType, Integer> totalMonitoringSent = new EnumMap<>(MonitoringMessageType.class);
    private Map<MonitoringMessageType, Integer> totalMonitoringReceived = new EnumMap<>(MonitoringMessageType.class);
    private Integer totalBlockTime;
    private Integer totalWaitTime;

    public MonitoringStateLogger(String instanceName) {
        this.instanceName = instanceName;
        initTotalMaps();
        initTotalTimes();
    }

    private void initTotalMaps() {
        for (MonitoringMessageType monitoringMessageType : MonitoringMessageType.values()) {
            totalMonitoringSent.put(monitoringMessageType, 0);
            totalMonitoringReceived.put(monitoringMessageType, 0);
        }
    }

    private void initTotalTimes() {
        totalBlockTime = 0;
        totalWaitTime = 0;
    }

    public void logBlockingMessage(String actorName, String calledMethod, String calledActor, VectorClock vc, long startTime, long stopTime) {
        totalBlockTime += (int) (stopTime - startTime);
        String log = String.format("%s is waiting on message (%s, %s, %s). \tvc: %s", instanceName, actorName, calledMethod, calledActor, vc);
       // System.out.println(log);
    }

    public void logWaitingMessage(String actorName, String calledMethod, String calledActor, VectorClock vc, long startTime, long stopTime) {
        totalWaitTime += (int) (stopTime - startTime);
        String log = String.format("%s is blocked on message (%s, %s, %s). \tvc: %s", instanceName, actorName, calledMethod, calledActor, vc);
        //System.out.println(log);
    }

    public Integer getTotalBlockTime() {
        return totalBlockTime;
    }

    public Integer getTotalWaitTime() {
        return totalWaitTime;
    }

    public void logSendMonitoringMessage(String receiver, Transition originTransition,
                                         MonitoringMessageType monitoringMessageType) {
        totalMonitoringSent.put(monitoringMessageType, totalMonitoringSent.get(monitoringMessageType) + 1);
        String log = String.format("A monitoring message of type %s is sending from %s to %s. Origin Transition: %s",
                monitoringMessageType.toString(), instanceName, receiver, getTransitionLog(originTransition));
        //System.out.println(log);
    }

    public void logReceiveRegularMessage(String caller, String callee, String methodName,
                                         RegularMessageType regularMessageType) {
        String log = String.format("A regular message of type %s is received to %s. Caller: %s, Callee: %s, " +
                "methodName: %s", regularMessageType.toString(), instanceName, caller, callee, methodName);
//        System.out.println(log);
    }

    public void logReceiveMonitoringMessage(MonitoringMessageMeta meta) {
        MonitoringMessageType monitoringMessageType = meta.getType();
        totalMonitoringReceived.put(monitoringMessageType, totalMonitoringReceived.get(monitoringMessageType) + 1);
        String log = String.format("A monitoring message of type %s is received to %s. Origin Transition: %s",
                monitoringMessageType.toString(), instanceName, getTransitionLog(meta.getOriginTransition()));
  //      System.out.println(log);
    }

    private String getTransitionLog(Transition transition) {
        return String.format("Caller: %s, Callee: %s, Called Method: %s", transition.getCaller(),
                transition.getCallee(), transition.getCalledMethod());
    }

    public int getTotalMonitoringSent(MonitoringMessageType monitoringMessageType) {
        return totalMonitoringSent.get(monitoringMessageType);
    }

    public int getTotalMonitoringReceived(MonitoringMessageType monitoringMessageType) {
        return totalMonitoringReceived.get(monitoringMessageType);
    }
}
