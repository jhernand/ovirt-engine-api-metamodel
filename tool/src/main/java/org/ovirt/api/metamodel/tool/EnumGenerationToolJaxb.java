/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.api.metamodel.tool;

import java.io.File;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.ovirt.api.metamodel.analyzer.ModelAnalyzer;
import org.ovirt.api.metamodel.concepts.Model;

@ApplicationScoped
public class EnumGenerationToolJaxb {

    // The names of the command line options:
    private static final String MODEL_OPTION = "model";
    private static final String XJC_OPTION = "xjc";

    @Inject private EnumGeneratorJaxb enumGenerator;

    public void run(String[] args) throws Exception {

        CommandLine line = createCommandLineOptions(args);

        File modelFile = (File) line.getParsedOptionValue(MODEL_OPTION);
        File xjcDir = (File) line.getParsedOptionValue(XJC_OPTION);

        // Analyze the model files:
        Model model = new Model();
        ModelAnalyzer modelAnalyzer = new ModelAnalyzer();
        modelAnalyzer.setModel(model);
        modelAnalyzer.analyzeSource(modelFile);

        // Generate the enums:
        if (xjcDir != null) {
            FileUtils.forceMkdir(xjcDir);
            enumGenerator.setOutDir(xjcDir);
            enumGenerator.generate(model);
        }
    }

    protected CommandLine createCommandLineOptions(String[] args) {
        // Create the command line options:
        Options options = new Options();

        options.addOption(Option.builder()
            .longOpt(MODEL_OPTION)
            .desc("The directory or .jar file containing the source model files.")
            .type(File.class)
            .required(true)
            .hasArg(true)
            .argName("DIRECTORY|JAR")
            .build()
        );

        options.addOption(Option.builder()
            .longOpt(XJC_OPTION)
            .desc("The directory where the generated model classes will be created.")
            .type(File.class)
            .required(false)
            .hasArg(true)
            .argName("DIRECTORY")
            .build()
        );

        // Parse the command line:
        CommandLineParser parser = new DefaultParser();
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
        }
        catch (ParseException exception) {
            System.out.println(exception);
            HelpFormatter formatter = new HelpFormatter();
            formatter.setSyntaxPrefix("Usage: ");
            formatter.printHelp("enum-generation--tool [OPTIONS]", options);
            System.exit(1);
        }
        return line;
    }
}
