package pt.up.fe.comp2025.backend;

import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.Collections;

/**
 * Robust and clean Jasmin backend implementation.
 */
public class JasminBackendImpl implements JasminBackend {

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        try {
            // Validate input
            if (ollirResult == null) {
                throw new IllegalArgumentException("OllirResult cannot be null");
            }

            if (ollirResult.getOllirClass() == null) {
                throw new IllegalArgumentException("OLLIR class cannot be null");
            }

            // Create generator and build Jasmin code
            JasminGenerator generator = new JasminGenerator(ollirResult);
            String jasminCode = generator.build();

            // Validate output
            if (jasminCode == null || jasminCode.trim().isEmpty()) {
                throw new RuntimeException("Generated Jasmin code is empty");
            }

            // Basic validation of generated code
            validateJasminCode(jasminCode);

            // Return successful result
            return new JasminResult(ollirResult, jasminCode, generator.getReports());

        } catch (Exception e) {
            // Create error report
            String errorMessage = "Jasmin generation failed: " + e.getMessage();
            Report errorReport = new Report(ReportType.ERROR, Stage.GENERATION, -1, -1, errorMessage);

            // Generate minimal valid Jasmin code to prevent further errors

            return new JasminResult(ollirResult,
                    null,
                    Collections.singletonList(errorReport));
        }
    }

    /**
     * Validate generated Jasmin code for basic correctness.
     */
    private void validateJasminCode(String jasminCode) {
        if (!jasminCode.contains(".class")) {
            throw new RuntimeException("Generated Jasmin code missing class declaration");
        }

        if (!jasminCode.contains(".super")) {
            throw new RuntimeException("Generated Jasmin code missing super class declaration");
        }

        if (!jasminCode.contains(".method")) {
            throw new RuntimeException("Generated Jasmin code missing method declarations");
        }

        if (!jasminCode.contains(".end method")) {
            throw new RuntimeException("Generated Jasmin code has unclosed method declarations");
        }
    }

}