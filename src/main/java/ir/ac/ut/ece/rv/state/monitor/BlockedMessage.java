package ir.ac.ut.ece.rv.state.monitor;

public class BlockedMessage extends Message {

    private BlockedMessageState state;

    public BlockedMessage(String methodName, String callee, String caller, BlockedMessageState state) {
        super(methodName,callee,caller);
        this.state = state;
    }

    public BlockedMessageState getState(){
        return state;
    }

    public void setState(BlockedMessageState state){
        this.state = state;
    }
}
