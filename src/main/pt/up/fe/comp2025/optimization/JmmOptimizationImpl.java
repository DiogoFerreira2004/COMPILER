package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;  // Added import
import pt.up.fe.comp.jmm.report.Stage;       // Added import
import pt.up.fe.comp2025.optimization.core.AstOptimizer;
import pt.up.fe.comp2025.optimization.core.OllirGenerator;
import pt.up.fe.comp2025.optimization.core.OllirOptimizer;

import java.lang.reflect.Field;  // Added for reflection
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main entry point for the optimization phase of the Java-- compiler.
 * Follows the Single Responsibility Principle by delegating specific optimization
 * tasks to specialized classes.
 */
public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        // Delegate OLLIR generation to specialized class

        return new OllirGenerator().generateOllir(semanticsResult);
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        // Check if optimizations are enabled
        boolean optimizeEnabled = Boolean.parseBoolean(
                semanticsResult.getConfig().getOrDefault("optimize", "false"));

        if (!optimizeEnabled) {
            return semanticsResult;
        }

        // Delegate AST-level optimizations to specialized class
        return new AstOptimizer(semanticsResult).optimize();
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        // Get register allocation configuration
        String registerAllocationStr = ollirResult.getConfig().getOrDefault("registerAllocation", "-1");
        int maxRegisters;

        try {
            maxRegisters = Integer.parseInt(registerAllocationStr);
        } catch (NumberFormatException e) {
            // If not a valid number, use default (-1)
            List<Report> reports = new ArrayList<>(ollirResult.getReports());
            reports.add(new Report(ReportType.WARNING, Stage.OPTIMIZATION, -1, -1,
                    "Invalid value for 'registerAllocation': " + registerAllocationStr +
                            ". Using default value (-1)"));
            return new OllirResult(getJmmSemanticsResult(ollirResult), ollirResult.getOllirCode(), reports);
        }

        // No register allocation if set to -1
        if (maxRegisters == -1) {
            return ollirResult;
        }

        // Delegate OLLIR-level optimizations to specialized class
        return new OllirOptimizer(ollirResult, maxRegisters).optimize();
    }

    /**
     * Gets the JmmSemanticsResult from an OllirResult.
     * Uses reflection to access the semanticsResult field; if it fails, creates a new one.
     */
    private JmmSemanticsResult getJmmSemanticsResult(OllirResult ollirResult) {
        try {
            // Try to use reflection to get the JmmSemanticsResult from ollirResult
            Field semanticsResultField = OllirResult.class.getDeclaredField("semanticsResult");
            semanticsResultField.setAccessible(true);
            return (JmmSemanticsResult) semanticsResultField.get(ollirResult);
        } catch (Exception e) {
            // If reflection fails, create a basic new JmmSemanticsResult
            return new JmmSemanticsResult(null, null, Collections.emptyList(), ollirResult.getConfig());
        }
    }
}