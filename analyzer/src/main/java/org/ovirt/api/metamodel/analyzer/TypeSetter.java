/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.api.metamodel.analyzer;

import org.ovirt.api.metamodel.concepts.Type;

import java.util.function.Consumer;

/**
 * This interface represents a function that can be used to change the reference to a type. For example, lets assume
 * that the type {@code MyType} has been referenced from an attribute, but not yet defined. The code that analyzes
 * the type will probably create a dummy type and it will need to remember to replace that dummy type with the real
 * type once it is known. To do so it can do the following:
 *
 * <pre>
 * // Create the attribute using a dummy type:
 * Attribute attribute = ...;
 * Type dummy = ...;
 * attribute.setType(dummy);
 *
 * // Remember to replace that type later:
 * TypeSetter setter = attribute::setType;
 *
 * ...
 *
 * // Once we know that is the real type we can replace it:
 * Type real = ...;
 * setter.accept(type);
 * </pre>
 *
 * These type setters can be easily remembered, for example using a list or a map.
 */
public interface TypeSetter extends Consumer<Type> {
}

