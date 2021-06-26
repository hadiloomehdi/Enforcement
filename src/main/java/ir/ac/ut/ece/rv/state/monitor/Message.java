package ir.ac.ut.ece.rv.state.monitor;

public class Message {

    private String methodName;
    private String callee;
    private String caller;

    public Message(String methodName, String callee, String caller) {
        this.methodName = methodName;
        this.callee = callee;
        this.caller = caller;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getCallee(){return callee;}

    public String getCaller(){return caller;}

    public boolean equal(String methodName, String callee, String caller){
        return methodName.equals(this.methodName) && callee.equals(this.callee) && caller.equals(this.caller);
    }
}
