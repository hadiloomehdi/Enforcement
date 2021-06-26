package ir.ac.ut.ece.rv.state.monitor;

import ir.ac.ut.ece.rv.state.MessageCall;
import ir.ac.ut.ece.rv.state.Value;
import ir.ac.ut.ece.rv.state.VectorClock;

import java.util.List;

public class TypedMessageCall extends MessageCall {

    private RegularMessageType type;

    public TypedMessageCall(String methodName, List<String> names, List<Value> values, VectorClock vectorClock, RegularMessageType type) {
        super(methodName, names, values, vectorClock);
        this.type = type;
    }

    public RegularMessageType getType(){return type;}
}
