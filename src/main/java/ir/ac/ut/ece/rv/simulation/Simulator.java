package ir.ac.ut.ece.rv.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.ac.ut.ece.rv.state.DelayManager;
import ir.ac.ut.ece.rv.state.GlobalState;
import ir.ac.ut.ece.rv.state.LocalState;
import ir.ac.ut.ece.rv.state.monitor.Interval;
import ir.ac.ut.ece.rv.state.monitor.MonitoringMessageType;
import ir.ac.ut.ece.rv.state.monitor.MonitoringState;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.RebecaModel;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Simulator {

    private RebecaModel rebecaModel;
    private Scheduler scheduler;
    private NextStateGenerator nextStateGenerator;
    private GlobalState state;

    private ObjectMapper mapper = new ObjectMapper();

    public Simulator(RebecaModel rebecaModel, boolean isStrict) {
        this.rebecaModel = rebecaModel;
        state = new GlobalState(rebecaModel, isStrict);
        nextStateGenerator = new NextStateGenerator(isStrict);
        state = nextStateGenerator.initialGlobalState(state);
        scheduler = new Scheduler();
    }

    public void run() {
        Set<Long> startingThreadIds =
                Thread.getAllStackTraces().keySet().stream().map(thread -> thread.getId()).collect(Collectors.toSet());
        boolean waitForDelay = false;
        while (true) {
            String selectedActor = scheduler.select(state);
            if (selectedActor == null) {
                if (!waitForDelay) {
                    waitForDelay = true;
                    try {
                        Thread.sleep(DelayManager.MAX_DELAY);
                    } catch (Exception ignored) {
                    }
                    continue;
                } else {
                    if (isSimulationFinished(startingThreadIds)) {
                        break;
                    } else
                        continue;
                }
            }
            waitForDelay = false;
            state = nextStateGenerator.generate(state, selectedActor);

            LocalState selectedActorState = state.getStates().get(selectedActor);
            //System.out.println("instance: " + selectedActor);
            //System.out.println("method:   " + state.getExecutingMethodName(selectedActor));
            //System.out.println("vc:       " + selectedActorState.getCopyOfVectorClock().toString());
            if (selectedActorState instanceof MonitoringState) {
                MonitoringState monitoringState = (MonitoringState) selectedActorState;
                int size = sizeof(monitoringState);
                monitoringState.setLatestSize(size);
             //   System.out.println("size:     " + size + " Bytes");
            }
//            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
            //System.out.println("--------------");
        }
    }

    private boolean isSimulationFinished(Set<Long> defaultThreads) {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            Thread.State state = thread.getState();
            ThreadGroup group = Thread.currentThread().getThreadGroup();

            if (state != Thread.State.TERMINATED && group.equals(thread.getThreadGroup()) &&
                    (!defaultThreads.contains(thread.getId()))) {
                return false;
            }
        }
        return true;
    }

    public GlobalState getState() {
        return state;
    }

    public void report() {
        long totalElapsedTime = 0;
        long totalWaitingTime = 0;
        long totalBlockingTime = 0;
        long totalMessageCount = 0;
        long totalAskToTellElapsedTime = 0;
        long totalTellToNotifyElapsedTime = 0;
        int totalSize = 0;
        int totalHistorySize = 0;
        Map<String, MonitoringState> monitors = state.getMonitors();
        Map<MonitoringMessageType, Integer> totalSentMessage = new EnumMap<>(MonitoringMessageType.class);
        for (MonitoringMessageType monitoringMessageType : MonitoringMessageType.values()) {
            totalSentMessage.put(monitoringMessageType, 0);
        }

        System.out.println("Final Report:");
        for (Map.Entry<String, MonitoringState> entry : monitors.entrySet()) {
            String name = entry.getKey();
            MonitoringState monitor = entry.getValue();
            System.out.println("--------------");
            System.out.println("Monitor '" + name + "'");

            for (MonitoringMessageType monitoringMessageType : MonitoringMessageType.values()) {
                System.out.println(String.format("Total received monitoring %ss: %d", monitoringMessageType.toString(),
                        monitor.getTotalMonitoringReceived(monitoringMessageType)));
            }

            for (MonitoringMessageType monitoringMessageType : MonitoringMessageType.values()) {
                System.out.println(String.format("Total sent monitoring %ss: %d", monitoringMessageType.toString(),
                        monitor.getTotalMonitoringSent(monitoringMessageType)));
                totalSentMessage.put(monitoringMessageType, totalSentMessage.get(monitoringMessageType) +
                        monitor.getTotalMonitoringSent(monitoringMessageType));
            }
            long monitorWaitTime = monitor.getTotalWaitingTime();
            long monitorBlockTime = monitor.getTotalBlockingTime();
            System.out.println("Total waiting time: " + monitorWaitTime + " ms");
            System.out.println("Total blocking time: " + monitorBlockTime + " ms");
            long monitorElapsedTime = 0;
            long monitorMessageCount = 0;
            for (Interval interval : monitor.getIntervals().values()) {
                monitorElapsedTime += interval.getDuration();
                monitorMessageCount++;
            }

            long avgElapsedTime = 0;
            if (monitorMessageCount != 0) {
                avgElapsedTime = monitorElapsedTime / monitorMessageCount;
            }
            System.out.println("Average monitor elapsed time: " + avgElapsedTime + " ms");

            int monitorSize = monitor.getLatestSize();
            int monitorHistorySize = monitor.getHistory().size();
            totalSize += monitorSize;
            totalHistorySize += monitorHistorySize;

            System.out.println("Size: " + monitorSize + " Bytes");
            System.out.println("History size: " + monitorHistorySize);

            long monitorAskToTellElapsedTime = 0;
            for (Interval interval : monitor.getAskToTellIntervals().values()) {
                monitorAskToTellElapsedTime += interval.getDuration();
            }
            System.out.println(String.format("Total monitor ASK to TELL elapsed time: %d ms",
                    monitorAskToTellElapsedTime));

            long monitorTellToNotifyElapsedTime = 0;
            for (Interval interval : monitor.getTellToNotifyIntervals().values()) {
                monitorTellToNotifyElapsedTime += interval.getDuration();
            }
            System.out.println(String.format("Total monitor TELL to NOTIFY elapsed time: %d ms",
                    monitorTellToNotifyElapsedTime));

            totalBlockingTime += monitorBlockTime;
            totalWaitingTime += monitorWaitTime;
            totalElapsedTime += monitorElapsedTime;
            totalMessageCount += monitorMessageCount;
            totalAskToTellElapsedTime += monitorAskToTellElapsedTime;
            totalTellToNotifyElapsedTime += monitorTellToNotifyElapsedTime;
        }
        System.out.println("--------------");


        for (Map.Entry<MonitoringMessageType, Integer> monitorMessageCount : totalSentMessage.entrySet()) {
            System.out.println(String.format("Total sent monitoring %ss from all monitors: %d",
                    monitorMessageCount.getKey().toString(),
                    monitorMessageCount.getValue()));
        }

        System.out.println("--------------");
        if (totalMessageCount != 0) {
            System.out.println(
                    "Total average elapsed time from all monitors: " + totalElapsedTime / totalMessageCount + " ms");
        } else {
            System.out.println("This actor has no messages");
        }

        System.out.println(String.format("Total ASK to TELL elapsed time from all monitors: %d ms",
                totalAskToTellElapsedTime));

        System.out.println(String.format("Total TELL to NOTIFY elapsed time from all monitors: %d ms",
                totalTellToNotifyElapsedTime));
        System.out.println("Total waiting time of all monitors: " + totalWaitingTime + " ms");
        System.out.println("Total blocking time of all monitors: " + totalBlockingTime + " ms");
        System.out.println("Total size of all monitors: " + totalSize + " Bytes");
        System.out.println("Total history size of all monitors " + totalHistorySize);
    }

    private int sizeof(MonitoringState monitoringState) {
        int historySize = monitoringState.getHistory().stream().mapToInt(this::sizeOf).sum();
        int mmListSize = monitoringState.getMmList().stream().mapToInt(this::sizeOf).sum();
        int tempHistorySize = monitoringState.getTempHistory().stream().mapToInt(this::sizeOf).sum();
        int vioHistorySize = monitoringState.getVioHistory().stream().mapToInt(this::sizeOf).sum();

        return historySize + mmListSize + tempHistorySize + vioHistorySize;
    }

    private int sizeOf(Object obj) {
        final int CHAR_SIZE = 2;
        try {
            return mapper.writeValueAsString(obj).length() * CHAR_SIZE;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }
}
