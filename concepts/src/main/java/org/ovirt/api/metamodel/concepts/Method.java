/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.api.metamodel.concepts;

import static org.ovirt.api.metamodel.concepts.Named.named;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * This class represents a method of a service.
 */
public class Method extends ServiceMember {

    private final Set<Name> NON_ACTION_METHOD_NAMES = new HashSet<>(Arrays.asList(
            new Name("add"), new Name("get"), new Name("list"), new Name("update"), new Name("remove")));
    //API methods with several signatures have a Method instance for each
    //signature and a base Method instance, containing common parameters.
    //for non-signature methods, 'base' will be null.
    private Method base;

    // The constraints that apply to this method:
    private List<Constraint> constraints = new ArrayList<>();

    /**
     * Returns the list of constraints that apply to this method. The returned list is a copy of the one used
     * internally, so it is safe to modify it in any way. If you aren't going to modify the list consider using the
     * {@link #constraints()} method instead.
     */
    public List<Constraint> getConstraints() {
        return new CopyOnWriteArrayList<>(constraints);
    }

    /**
     * Returns a stream that delivers the constraints of this method.
     */
    public Stream<Constraint> constraints() {
        return constraints.stream();
    }

    /**
     * Returns the constraint that has the given name, or {@code null} if there is no such constraint.
     */
    public Constraint getConstraint(Name name) {
        return constraints.stream().filter(named(name)).findFirst().orElse(null);
    }

    /**
     * Adds a new constraint to this method.
     */
    public void addConstraint(Constraint constraint) {
        constraints.add(constraint);
    }

    /**
     * Adds a list of new constraints to this method.
     */
    public void addConstraints(List<Constraint> constraints) {
        this.constraints.addAll(constraints);
    }

    /**
     * Determines if this is an action method, i.e., any method whose name isn't {@code Add}, {@code Get}, {@code List},
     * {@code Update} or {@code Remove}.
     */
    public boolean isAction() {
        return !(NON_ACTION_METHOD_NAMES.contains(getName())
                || (base!=null && NON_ACTION_METHOD_NAMES.contains(base.getName())));
    }

    public Method getBase() {
        return base;
    }

    public void setBase(Method base) {
        this.base = base;
    }

    public boolean isMandatoryAttributeExists() {
        for (Parameter parameter : getParameters()) {
            if (parameter.isMandatory() || !parameter.getMandatoryAttributes().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public List<MemberInvolvementTree> getMandatoryAttributes() {
        List<MemberInvolvementTree> mandatoryAttributes = new ArrayList<>();
        for (Parameter parameter : getParameters()) {
            mandatoryAttributes.addAll(parameter.getMandatoryAttributes());
        }
        return mandatoryAttributes;
    }

    public List<Parameter> getMandatoryParameters() {
        List<Parameter> parameters = new ArrayList<>();
        for (Parameter parameter : getParameters()) {
            if (parameter.isMandatory()) {
                parameters.add(parameter);
            }
        }
        return parameters;
    }
}
