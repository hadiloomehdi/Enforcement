package ir.ac.ut.ece.rv;

import ir.ac.ut.ece.rv.simulation.Simulator;
import ir.ac.ut.ece.rv.state.monitor.TransitionTable;
import ir.ac.ut.ece.rv.utility.RebecaCompilerUtility;
import org.rebecalang.compiler.modelcompiler.corerebeca.objectmodel.RebecaModel;

public class Main {
    private static String BASE_PATH = "src/main/resources/input";
    private static String ExecutionType = "conservative";

    public static void main(String[] args) {
        parseArguments(args);

        boolean isStrict = (ExecutionType.equals("strict"));
        RebecaModel rebecaModel = RebecaCompilerUtility.getRebecaModelOf(BASE_PATH + "/output.rebec");
        Simulator simulator = new Simulator(rebecaModel, isStrict);

        simulator.run();
        simulator.report();
    }

    private static void parseArguments(String[] args) {
        try {
            for (int i = 0; i < args.length; i += 2) {
                if (args[i].equalsIgnoreCase("-im") || args[i].equalsIgnoreCase("--model")) {
                    BASE_PATH = args[i + 1];
                } else if (args[i].equalsIgnoreCase("-it") || args[i].equalsIgnoreCase("--tables")) {
                    TransitionTable.setGeneralCSVPath(args[i + 1]);
                } else if (args[i].equalsIgnoreCase("-t") || args[i].equalsIgnoreCase("--type")) {
                    String type = args[i + 1];
                    if (type.equalsIgnoreCase("strict") || type.equalsIgnoreCase("conservative")) {
                        ExecutionType = type;
                    } else {
                        throw new IllegalArgumentException();
                    }
                } else {
                    throw new IllegalArgumentException();
                }
            }
        } catch (IndexOutOfBoundsException e) {
            System.out.println("Invalid Input for Argument.");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown Argument.");
            System.exit(1);
        }
    }

}