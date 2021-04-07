/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.api.metamodel.tool;

import static java.util.stream.Collectors.joining;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.ovirt.api.metamodel.concepts.Name;
import org.ovirt.api.metamodel.concepts.Service;

/**
 * This class contains the rules used to calculate the names of generated JAX-RS interfaces.
 */
@ApplicationScoped
public class JaxrsNames {
    // The suffix that will be added to interface names:
    private static final String RESOURCE_SUFFIX = "Resource";
    private static final String HELPER_SUFFIX = "Helper";

    // References to objects used to do calculations with words:
    @Inject Words words;

    // References to objects used to calculate Java names:
    @Inject JavaPackages javaPackages;
    @Inject JavaNames javaNames;

    /**
     * Calculates the name of the JAX-RS interface that corresponds to the given service.
     */
    public JavaClassName getInterfaceName(Service service) {
        JavaClassName name = new JavaClassName();
        String packageName = javaPackages.getJaxrsPackageName(service.getModule());
        String simpleName = javaNames.getJavaClassStyleName(service.getName()) + RESOURCE_SUFFIX;
        name.setPackageName(packageName);
        name.setSimpleName(simpleName);
        return name;
    }

    /**
     * Calculates the JAX-RS method that corresponds to the given method name.
     */
    public String getMethodName(Name name) {
        // The object that calculates Java names adds "_" as a suffix to the name if it clashes with a Java reserved
        // word, but in the JAX-RS interface the rules is to add "do" as a prefix instead, so we need to adapt the
        // calculated name:
        String result = javaNames.getJavaMemberStyleName(name);
        if (result.endsWith("_")) {
            result = "do" + words.capitalize(result.substring(0, result.length() - 1));
        }
        return result;
    }

    /**
     * Checks if the given name corresponds to an action method.
     */
    public boolean isActionName(Name name) {
        switch (name.toString()) {
        case "add":
        case "get":
        case "list":
        case "remove":
        case "update":
            return false;
        default:
            return true;
        }
    }

    /**
     * Calculates the path corresponding to the given method. This is calculated converting all the words of the
     * name to lowercase and concatenating them without separators. For example, if the name of the action is
     * {@code commitSnapshot} then the returned string will be {@code commitsnapshot}.
     */
    public String getActionPath(Name name) {
        return name.words().map(String::toLowerCase).collect(joining());
    }

    /**
     * Calculates the name of the JAX-RS helper class for the given service.
     */
    public JavaClassName getHelperName(Service service) {
        JavaClassName name = new JavaClassName();
        name.setPackageName(javaPackages.getJaxrsPackageName(service.getModule()));
        name.setSimpleName(javaNames.getJavaClassStyleName(service.getName()) + RESOURCE_SUFFIX + HELPER_SUFFIX);
        return name;
    }
}

