/*
Copyright (c) 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.ovirt.api.metamodel.tool;

import org.ovirt.api.metamodel.concepts.Type;

/**
 * This interface specifies the rules used to calculate the names of generated Java types and type references.
 */
public interface JavaTypes {
    /**
     * Calculates the name of the interface that should be generated for the given type. For example, for
     * the {@code Vm} type it will generae {@code org.ovirt.engine.model} as the package name and {@code V4Vm} as
     * the simple class name.
     */
    JavaClassName getInterfaceName(Type type);

    /**
     * Calculates the name of the container class that should be generated for the given type. For example,
     * for the {@code Vm} type it will generate {@code org.ovirt.engine.model} as the package name and
     * {@code V4VmContainer} as the simple class name.
     */
    JavaClassName getContainerName(Type type);

    /**
     * Calculates the name of the builder class that should be generated for the given type. For example,
     * for the {@code Vm} type it will generate {@code org.ovirt.engine.model} as the package name and
     * {@code V4VmBuilder} as the simple class name.
     */
    JavaClassName getBuilderName(Type type);

    /**
     * Calculates the type reference that should be generated for the given type. For example, for the type
     * {@code Vm[]} it returns the text {@code List<Vm>} and a list of imports containing {@code java.util.List}
     * and {@code org.ovirt.engine.api.model.V4Vm}.
     */
    JavaTypeReference getTypeReference(Type type);
}
