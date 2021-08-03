package ir.ac.ut.ece.rv.state.monitor;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TransitionItem {
    private Transition pre;
    private Transition transition;
    private List<Transition> vio;
    private boolean isFinal;
    private boolean isDdind;
    private boolean isPreind;
    private HashMap<String, Boolean> vioind;
    private boolean isInit;

    public TransitionItem(Transition pre, Transition transition, List<Transition> vio, boolean isFinal, boolean isDdinit, boolean isPreind, List<Boolean> vioind, boolean isInit) {
        this.pre = pre;
        this.transition = transition;
        this.vio = vio;
        this.isFinal = isFinal;
        this.isDdind = isDdinit;
        this.isPreind = isPreind;
        this.vioind = (vio.size() == 0) ? new HashMap<>() : new HashMap<>(IntStream.range(0, vio.size()).boxed().collect(Collectors.toMap(i -> vio.get(i).toString(), vioind::get)));
        this.isInit = isInit;
    }

    public boolean isForMethod(String methodName) {
        return transition.getCalledMethod().equals(methodName);
    }

    public Transition getPre() {
        return pre;
    }

    public Transition getTransition() {
        return transition;
    }

    public List<Transition> getVio() {
        return vio;
    }

    public boolean isFinal() {
        return isFinal;
    }


    public boolean isDdind() {
        return isDdind;
    }

    public boolean isPreind() {
        return isPreind;
    }

    public boolean isInit() {
        return isInit;
    }

    public List<Transition> getVoiIndTransitions(){
        return vio.stream()
                .filter(transition -> vioind.getOrDefault(transition.toString(), false))
                .collect(Collectors.toList());
    }


}
