/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.api.metamodel.tool;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;

import org.ovirt.api.metamodel.concepts.EnumType;
import org.ovirt.api.metamodel.concepts.EnumValue;
import org.ovirt.api.metamodel.concepts.ListType;
import org.ovirt.api.metamodel.concepts.Model;
import org.ovirt.api.metamodel.concepts.Name;
import org.ovirt.api.metamodel.concepts.NameParser;
import org.ovirt.api.metamodel.concepts.PrimitiveType;
import org.ovirt.api.metamodel.concepts.StructMember;
import org.ovirt.api.metamodel.concepts.StructType;
import org.ovirt.api.metamodel.concepts.Type;
import org.ovirt.api.metamodel.runtime.util.ArrayListWithHref;
import org.ovirt.api.metamodel.runtime.util.ListWithHref;
import org.ovirt.api.metamodel.runtime.util.UnmodifiableListWithHref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class generates the interfaces and classes corresponding to the types of the model.
 */
public class TypesGenerator extends JavaGenerator {
    // Reference to the object that calculates names:
    @Inject @Style("versioned") private JavaNames javaNames;
    @Inject private JavaPackages javaPackages;
    @Inject private JavaTypes javaTypes;
    @Inject private Names names;

    public void generate(Model model) {
        // Generate classes for each enum type:
        model.types()
            .filter(EnumType.class::isInstance)
            .map(EnumType.class::cast)
            .forEach(this::generateEnum);

        // Generate classes for each struct type:
        model.types()
            .filter(StructType.class::isInstance)
            .map(StructType.class::cast)
            .forEach(this::generateClasses);

        // Generate a class that has static method to create builders:
        generateBuildersFactory(model);
    }

    private void generateEnum(EnumType type) {
        javaBuffer = new JavaClassBuffer();
        JavaClassName enumName = javaTypes.getEnumName(type);
        javaBuffer.setClassName(enumName);
        generateEnumSource(type);
        try {
            javaBuffer.write(outDir);
        }
        catch (IOException exception) {
            throw new RuntimeException("Can't write file for enum \"" + enumName + "\"", exception);
        }
    }

    private void generateEnumSource(EnumType type) {
        // Generate the documentation:
        generateDoc(type);

        // Begin enum:
        JavaClassName enumName = javaTypes.getEnumName(type);
        javaBuffer.addLine("public enum %1$s {", enumName.getSimpleName());

        // Generate the declarations of the values:
        type.values().sorted().forEach(this::generateEnumValue);
        javaBuffer.addLine(";");
        javaBuffer.addLine();

        // Generate the logger:
        javaBuffer.addImport(Logger.class);
        javaBuffer.addImport(LoggerFactory.class);
        javaBuffer.addLine(
            "private static final Logger log = LoggerFactory.getLogger(%1$s.class);",
            enumName.getSimpleName()
        );
        javaBuffer.addLine();

        // Generate the field that stores the image:
        javaBuffer.addLine("private String image;");
        javaBuffer.addLine();

        // Generate the constructor:
        javaBuffer.addLine("%1$s(String image) {", enumName.getSimpleName());
        javaBuffer.addLine(  "this.image = image;");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // Generate the method that converts the enum to an string:
        javaBuffer.addLine("public String value() {");
        javaBuffer.addLine(  "return image;");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // Generate the method that creates an instance from an string:
        javaBuffer.addLine("public static %1$s fromValue(String value) {", enumName.getSimpleName());
        javaBuffer.addLine(  "try {");
        javaBuffer.addLine(    "return valueOf(value.toUpperCase());");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "catch (IllegalArgumentException exception) {");
        javaBuffer.addLine(    "log.error(");
        javaBuffer.addLine(
            "\"The string '\" + value + \"' isn't a valid value for the '%1$s' enumerated type. \" +",
            enumName.getSimpleName()
        );
        List<String> images = type.values()
            .map(this::getEnumValueImage)
            .sorted()
            .collect(toList());
        if (images.size() == 1) {
            javaBuffer.addLine("\"Valid value is '%1$s'.\",", images.get(0));
        }
        else {
            String head = images.stream()
                .limit(images.size() - 1)
                .map(image -> "'" + image + "'")
                .collect(joining(", "));
            String tail = images.get(images.size() - 1);
            javaBuffer.addLine( "\"Valid values are %1$s and '%2$s'.\",", head, tail);
        }
        javaBuffer.addLine(      "exception");
        javaBuffer.addLine(    ");");
        javaBuffer.addLine(    "return null;");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // End enum:
        javaBuffer.addLine("}");
        javaBuffer.addLine();
    }

    private void generateEnumValue(EnumValue value) {
        // Generate the documentation:
        generateDoc(value);

        // Generate the declaration of the value:
        javaBuffer.addLine("%1$s(\"%2$s\"),", getEnumValueName(value), getEnumValueImage(value));
    }

    private String getEnumValueName(EnumValue value) {
        return names.getUpperJoined(value.getName(), "_");
    }

    private String getEnumValueImage(EnumValue value) {
        return names.getLowerJoined(value.getName(), "_");
    }

    private void generateClasses(StructType type) {
        generateInterface(type);
        generateBaseContainer();
        generateContainer(type);
        generateBuilder(type);
    }

    private void generateInterface(StructType type) {
        javaBuffer = new JavaClassBuffer();
        JavaClassName typeName = javaTypes.getInterfaceName(type);
        javaBuffer.setClassName(typeName);
        generateInterfaceSource(type);
        try {
            javaBuffer.write(outDir);
        }
        catch (IOException exception) {
            throw new RuntimeException("Can't write file for interface \"" + typeName + "\"", exception);
        }
    }

    private void generateInterfaceSource(StructType type) {
        // Generate the documentation:
        generateDoc(type);

        // Begin class:
        JavaClassName interfaceName = javaTypes.getInterfaceName(type);
        Type base = type.getBase();
        if (base != null) {
            JavaClassName baseName = javaTypes.getInterfaceName(base);
            javaBuffer.addImport(baseName);
            javaBuffer.addLine(
                "public interface %1$s extends %2$s {",
                interfaceName.getSimpleName(),
                baseName.getSimpleName()
            );
        }
        else {
            javaBuffer.addLine("public interface %s {", interfaceName.getSimpleName());
        }

        // Attributes and links:
        type.declaredAttributes().sorted().forEach(this::generateInterfaceMembers);
        type.declaredLinks().sorted().forEach(this::generateInterfaceMembers);

        // End class:
        javaBuffer.addLine("}");
    }

    private void generateInterfaceMembers(StructMember member) {
        // Get the name of the property:
        Name name = member.getName();
        Type type = member.getType();
        Model model = type.getModel();
        String field = javaNames.getJavaMemberStyleName(name);

        // Get the type reference:
        JavaTypeReference typeReference = javaTypes.getTypeReference(type, false);
        javaBuffer.addImports(typeReference.getImports());

        // Generate the getters:
        javaBuffer.addLine("%1$s %2$s();", typeReference, field);
        if (type == model.getIntegerType()) {
            javaBuffer.addLine("Byte %1$sAsByte();", field);
            javaBuffer.addLine("Short %1$sAsShort();", field);
            javaBuffer.addLine("Integer %1$sAsInteger();", field);
            javaBuffer.addLine("Long %1$sAsLong();", field);
        }
        javaBuffer.addLine();

        // Generate the checker:
        javaBuffer.addLine("boolean %1$sPresent();", field);
        javaBuffer.addLine();
    }

    private void generateBaseContainer() {
        javaBuffer = new JavaClassBuffer();
        JavaClassName containerName = javaTypes.getBaseContainerName();
        javaBuffer.setClassName(containerName);
        generateBaseContainerSource();
        try {
            javaBuffer.write(outDir);
        }
        catch (IOException exception) {
            throw new RuntimeException("Can't write file for base container \"" + containerName + "\"", exception);
        }
    }

    private void generateBaseContainerSource() {
        // Imports:
        javaBuffer.addImport(ArrayList.class);
        javaBuffer.addImport(ArrayListWithHref.class);
        javaBuffer.addImport(Collections.class);
        javaBuffer.addImport(List.class);
        javaBuffer.addImport(ListWithHref.class);
        javaBuffer.addImport(UnmodifiableListWithHref.class);

        // Begin class:
        JavaClassName containerName = javaTypes.getBaseContainerName();
        javaBuffer.addLine("public class %1$s {", containerName.getSimpleName());

        // Method to make a byte:
        javaBuffer.addImport(BigInteger.class);
        javaBuffer.addLine("protected static Byte asByte(String type, String member, BigInteger value) {");
        javaBuffer.addLine(  "if (value == null) {");
        javaBuffer.addLine(    "return null;");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "try {");
        javaBuffer.addLine(    "return value.byteValueExact();");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "catch (ArithmeticException excetion) {");
        javaBuffer.addLine(    "throw new ArithmeticException(");
        javaBuffer.addLine(      "\"The integer value \" + value + \" of the '\" + member + \"' member of \" +");
        javaBuffer.addLine(      "\"type '\" + type + \"' can't be converted to a 8 bits integer because that \" +");
        javaBuffer.addLine(      "\"would loss precision.\"");
        javaBuffer.addLine(    ");");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // Method to make a short:
        javaBuffer.addImport(BigInteger.class);
        javaBuffer.addLine("protected static Short asShort(String type, String member, BigInteger value) {");
        javaBuffer.addLine(  "if (value == null) {");
        javaBuffer.addLine(    "return null;");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "try {");
        javaBuffer.addLine(    "return value.shortValueExact();");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "catch (ArithmeticException exception) {");
        javaBuffer.addLine(    "throw new ArithmeticException(");
        javaBuffer.addLine(      "\"The integer value \" + value + \" of the '\" + member + \"' member of \" +");
        javaBuffer.addLine(      "\"type '\" + type + \"' can't be converted to a 16 bits integer because that \" +");
        javaBuffer.addLine(      "\"would loss precision.\"");
        javaBuffer.addLine(    ");");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // Method to make an integer:
        javaBuffer.addImport(BigInteger.class);
        javaBuffer.addLine("protected static Integer asInteger(String type, String member, BigInteger value) {");
        javaBuffer.addLine(  "if (value == null) {");
        javaBuffer.addLine(    "return null;");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "try {");
        javaBuffer.addLine(    "return value.intValueExact();");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "catch (ArithmeticException exception) {");
        javaBuffer.addLine(    "throw new ArithmeticException(");
        javaBuffer.addLine(      "\"The integer value \" + value + \" of the '\" + member + \"' member of \" +");
        javaBuffer.addLine(      "\"type '\" + type + \"' can't be converted to a 32 bits integer because that \" +");
        javaBuffer.addLine(      "\"would loss precision.\"");
        javaBuffer.addLine(    ");");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // Method to make a long:
        javaBuffer.addImport(BigInteger.class);
        javaBuffer.addLine("protected static Long asLong(String type, String member, BigInteger value) {");
        javaBuffer.addLine(  "if (value == null) {");
        javaBuffer.addLine(    "return null;");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "try {");
        javaBuffer.addLine(    "return value.longValueExact();");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "catch (ArithmeticException exception) {");
        javaBuffer.addLine(    "throw new ArithmeticException(");
        javaBuffer.addLine(      "\"The integer value \" + value + \" of the '\" + member + \"' member of \" +");
        javaBuffer.addLine(      "\"type '\" + type + \"' can't be converted to a 64 bits integer because that \" +");
        javaBuffer.addLine(      "\"would loss precision.\"");
        javaBuffer.addLine(    ");");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // Method to make an unmodifiable list:
        javaBuffer.addLine("protected static <E> List<E> makeUnmodifiableList(List<E> original) {");
        javaBuffer.addLine(  "if (original == null) {");
        javaBuffer.addLine(    "return Collections.emptyList();");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "else {");
        javaBuffer.addLine(    "if (original instanceof ListWithHref) {");
        javaBuffer.addLine(      "return new UnmodifiableListWithHref((ListWithHref) original);");
        javaBuffer.addLine(    "}");
        javaBuffer.addLine(    "return Collections.unmodifiableList(original);");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine("}");
        javaBuffer.addLine();

        // Method to make an array list:
        javaBuffer.addLine("protected static <E> List<E> makeArrayList(List<E> original) {");
        javaBuffer.addLine(  "if (original == null) {");
        javaBuffer.addLine(    "return Collections.emptyList();");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine(  "else {");
        javaBuffer.addLine(    "if (original instanceof ListWithHref) {");
        javaBuffer.addLine(      "return new ArrayListWithHref<E>((ListWithHref) original);");
        javaBuffer.addLine(    "}");
        javaBuffer.addLine(    "return new ArrayList<E>(original);");
        javaBuffer.addLine(  "}");
        javaBuffer.addLine("}");

        // End class:
        javaBuffer.addLine("}");
    }

    private void generateContainer(StructType type) {
        javaBuffer = new JavaClassBuffer();
        JavaClassName containerName = javaTypes.getContainerName(type);
        javaBuffer.setClassName(containerName);
        generateContainerSource(type);
        try {
            javaBuffer.write(outDir);
        }
        catch (IOException exception) {
            throw new RuntimeException("Can't write file for container \"" + containerName + "\"", exception);
        }
    }

    private void generateContainerSource(StructType type) {
        // Begin class:
        JavaClassName typeName = javaTypes.getInterfaceName(type);
        JavaClassName containerName = javaTypes.getContainerName(type);
        Type base = type.getBase();
        JavaClassName baseName = base != null? javaTypes.getContainerName(base): javaTypes.getBaseContainerName();
        javaBuffer.addImport(typeName);
        javaBuffer.addImport(baseName);
        javaBuffer.addLine("public class %1$s extends %2$s implements %3$s {",
            containerName.getSimpleName(), baseName.getSimpleName(), typeName.getSimpleName());

        // Fields for attributes and links:
        type.declaredAttributes().sorted().forEach(this::generateContainerFields);
        type.declaredLinks().sorted().forEach(this::generateContainerFields);
        javaBuffer.addLine();

        // Methods for attributes and links:
        type.declaredAttributes().sorted().forEach(this::generateContainerMethods);
        type.declaredLinks().sorted().forEach(this::generateContainerMethods);

        // End class:
        javaBuffer.addLine("}");
    }

    private void generateContainerFields(StructMember member) {
        // Get the name of the field:
        Name name = member.getName();
        String field = javaNames.getJavaMemberStyleName(name);

        // Get the type reference:
        Type type = member.getType();
        JavaTypeReference typeReference = javaTypes.getTypeReference(type, true);
        javaBuffer.addImports(typeReference.getImports());

        // Generate the field:
        javaBuffer.addLine("private %1$s %2$s;", typeReference.getText(), field);
    }

    private void generateContainerMethods(StructMember member) {
        // Get the name of the field:
        Name name = member.getName();
        Type type = member.getType();
        Model model = type.getModel();
        String field = javaNames.getJavaMemberStyleName(name);
        String property = javaNames.getJavaPropertyStyleName(name);
        String declaring = javaNames.getJavaClassStyleName(member.getDeclaringType().getName());

        // Get the type reference:
        JavaTypeReference typeReference = javaTypes.getTypeReference(type, false);

        // Generate the getters:
        if (type == model.getIntegerType()) {
            javaBuffer.addImport(BigInteger.class);
            javaBuffer.addLine("public BigInteger %1$s() {", field);
            javaBuffer.addLine(  "return %1$s;", field);
            javaBuffer.addLine("}");
            javaBuffer.addLine();

            javaBuffer.addLine("public Byte %1$sAsByte() {", field);
            javaBuffer.addLine(  "return asByte(\"%1$s\", \"%2$s\", %2$s);", declaring, field);
            javaBuffer.addLine("}");
            javaBuffer.addLine();

            javaBuffer.addLine("public Short %1$sAsShort() {", field);
            javaBuffer.addLine(  "return asShort(\"%1$s\", \"%2$s\", %2$s);", declaring, field);
            javaBuffer.addLine("}");
            javaBuffer.addLine();

            javaBuffer.addLine("public Integer %1$sAsInteger() {", field);
            javaBuffer.addLine(  "return asInteger(\"%1$s\", \"%2$s\", %2$s);", declaring, field);
            javaBuffer.addLine("}");
            javaBuffer.addLine();

            javaBuffer.addLine("public Long %1$sAsLong() {", field);
            javaBuffer.addLine(  "return asLong(\"%1$s\", \"%2$s\", %2$s);", declaring, field);
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }
        else if (type == model.getDateType()) {
            javaBuffer.addImport(Date.class);
            javaBuffer.addLine("public Date %1$s() {", field);
            javaBuffer.addLine(  "if (%1$s == null) {", field);
            javaBuffer.addLine(    "return null;");
            javaBuffer.addLine(  "}");
            javaBuffer.addLine(  "else {");
            javaBuffer.addLine(    "return new Date(%1$s.getTime());", field);
            javaBuffer.addLine(  "}");
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }
        else if (type instanceof ListType) {
            javaBuffer.addImports(typeReference.getImports());
            javaBuffer.addLine("public %1$s %2$s() {", typeReference.getText(), field);
            javaBuffer.addLine(  "return makeUnmodifiableList(%1$s);", field);
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }
        else {
            javaBuffer.addImports(typeReference.getImports());
            javaBuffer.addLine("public %1$s %2$s() {", typeReference.getText(), field);
            javaBuffer.addLine(  "return %1$s;", field);
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }

        // Generate the setter:
        if (type instanceof PrimitiveType) {
            if (type == model.getBooleanType()) {
                // Generate the method that takes a "boolean" parameter:
                javaBuffer.addLine("public void %1$s(boolean new%2$s) {", field, property);
                javaBuffer.addLine(  "%1$s = Boolean.valueOf(new%2$s);", field, property);
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Generate the method that takes a "Boolean" parameter:
                javaBuffer.addLine("public void %1$s(Boolean new%2$s) {", field, property);
                javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
            else if (type == model.getDateType()) {
                javaBuffer.addImport(Date.class);
                javaBuffer.addLine("public void %1$s(Date new%2$s) {", field, property);
                javaBuffer.addLine(  "if (new%1$s == null) {", property);
                javaBuffer.addLine(    "%1$s = null;", field);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "else {");
                javaBuffer.addLine(    "%1$s = new Date(new%2$s.getTime());", field, property);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
            else {
                javaBuffer.addLine("public void %1$s(%2$s new%3$s) {", field, typeReference.getText(), property);
                javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
        }
        else if (type instanceof ListType) {
            javaBuffer.addLine("public void %1$s(%2$s new%3$s) {", field, typeReference.getText(), property);
            javaBuffer.addLine(  "%1$s = makeArrayList(new%2$s);", field, property);
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }
        else {
            javaBuffer.addLine("public void %1$s(%2$s new%3$s) {", field, typeReference.getText(), property);
            javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }

        // Generate the checker:
        javaBuffer.addLine("public boolean %1$sPresent() {", field);
        if (type instanceof ListType) {
            javaBuffer.addLine("return %1$s != null && !%1$s.isEmpty();", field);
        }
        else {
            javaBuffer.addLine("return %1$s != null;", field);
        }
        javaBuffer.addLine("}");
        javaBuffer.addLine();
    }

    private void generateBuilder(StructType type) {
        javaBuffer = new JavaClassBuffer();
        JavaClassName containerName = javaTypes.getBuilderName(type);
        javaBuffer.setClassName(containerName);
        generateBuilderSource(type);
        try {
            javaBuffer.write(outDir);
        }
        catch (IOException exception) {
            throw new RuntimeException("Can't write file for container \"" + containerName + "\"", exception);
        }
    }

    private void generateBuilderSource(StructType type) {
        // Begin class:
        JavaClassName builderName = javaTypes.getBuilderName(type);
        javaBuffer.addLine("public class %1$s {", builderName.getSimpleName());

        // Generate the fields for attributes and links:
        Stream.concat(type.attributes(), type.links()).sorted().forEach(this::generateBuilderFields);
        javaBuffer.addLine();

        // Generate the methods for attributes and links:
        Stream.concat(type.attributes(), type.links())
            .sorted()
            .forEach(member -> generateBuilderMethods(type, member));

        // Generate the "build" method:
        JavaClassName typeName = javaTypes.getInterfaceName(type);
        JavaClassName containerName = javaTypes.getContainerName(type);
        javaBuffer.addImport(typeName);
        javaBuffer.addImport(containerName);
        javaBuffer.addLine("public %1$s build() {", typeName.getSimpleName());
        javaBuffer.addLine(  "%1$s container = new %1$s();", containerName.getSimpleName());
        Stream.concat(type.attributes(), type.links()).sorted().forEach(member -> {
            Name name = member.getName();
            String field = javaNames.getJavaMemberStyleName(name);
            javaBuffer.addLine("container.%1$s(%1$s);", field);
        });
        javaBuffer.addLine(  "return container;");
        javaBuffer.addLine("}");

        // End class:
        javaBuffer.addLine("}");
    }

    private void generateBuilderFields(StructMember member) {
        // Get the name of the property:
        Name name = member.getName();
        String field = javaNames.getJavaMemberStyleName(name);

        // Get the type reference:
        Type type = member.getType();
        JavaTypeReference typeReference = javaTypes.getTypeReference(type, true);
        javaBuffer.addImports(typeReference.getImports());

        // Generate the field:
        javaBuffer.addLine("private %1$s %2$s;", typeReference.getText(), field);
    }

    private void generateBuilderMethods(StructType struct, StructMember member) {
        // Get the name of the property:
        Name name = member.getName();
        String field = javaNames.getJavaMemberStyleName(name);
        String property = javaNames.getJavaPropertyStyleName(name);

        // Get the type reference of the property:
        Type type = member.getType();
        JavaTypeReference typeReference = javaTypes.getTypeReference(type, false);
        JavaClassName thisName = javaTypes.getBuilderName(struct);
        javaBuffer.addImports(typeReference.getImports());
        javaBuffer.addImport(thisName);

        // Generate the setter:
        if (type instanceof PrimitiveType) {
            Model model = type.getModel();
            if (type == model.getBooleanType()) {
                // Add one method that takes "boolean" as parameter:
                javaBuffer.addLine("public %1$s %2$s(boolean new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = Boolean.valueOf(new%2$s);", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "Boolean" as parameter:
                javaBuffer.addLine("public %1$s %2$s(Boolean new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
            else if (type == model.getIntegerType()) {
                // Add one method that takes "int" as parameter:
                javaBuffer.addImport(BigInteger.class);
                javaBuffer.addLine("public %1$s %2$s(int new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = BigInteger.valueOf((long) new%2$s);", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "Integer" as parameter:
                javaBuffer.addImport(BigInteger.class);
                javaBuffer.addLine("public %1$s %2$s(Integer new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "if (new%1$s == null) {", property);
                javaBuffer.addLine(    "%1$s = null;", field);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "else {");
                javaBuffer.addLine(    "%1$s = BigInteger.valueOf(new%2$s.longValue());", field, property);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "long" as parameter:
                javaBuffer.addImport(BigInteger.class);
                javaBuffer.addLine("public %1$s %2$s(long new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = BigInteger.valueOf(new%2$s);", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "Long" as parameter:
                javaBuffer.addImport(BigInteger.class);
                javaBuffer.addLine("public %1$s %2$s(Long new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "if (new%1$s == null) {", property);
                javaBuffer.addLine(    "%1$s = null;", field);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "else {");
                javaBuffer.addLine(    "%1$s = BigInteger.valueOf(new%2$s.longValue());", field, property);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "BigInteger" as parameter:
                javaBuffer.addImport(BigInteger.class);
                javaBuffer.addLine("public %1$s %2$s(BigInteger new%3$s) {", thisName.getSimpleName(), field,
                    property);
                javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
            else if (type == model.getDecimalType()) {
                // Add one method that takes "float" as parameter:
                javaBuffer.addImport(BigDecimal.class);
                javaBuffer.addLine("public %1$s %2$s(float new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = BigDecimal.valueOf((double) new%2$s);", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "Float" as parameter:
                javaBuffer.addImport(BigDecimal.class);
                javaBuffer.addLine("public %1$s %2$s(Float new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "if (new%1$s == null) {", property);
                javaBuffer.addLine(    "%1$s = null;", field);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "else {");
                javaBuffer.addLine(    "%1$s = BigDecimal.valueOf(new%2$s.doubleValue());", field, property);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "double" as parameter:
                javaBuffer.addImport(BigDecimal.class);
                javaBuffer.addLine("public %1$s %2$s(double new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = BigDecimal.valueOf(new%2$s);", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "Double" as parameter:
                javaBuffer.addImport(BigDecimal.class);
                javaBuffer.addLine("public %1$s %2$s(Double new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "if (new%1$s == null) {", property);
                javaBuffer.addLine(    "%1$s = null;", field);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "else {");
                javaBuffer.addLine(    "%1$s = BigDecimal.valueOf(new%2$s.doubleValue());", field, property);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();

                // Add one method that takes "BigDecimal" as parameter:
                javaBuffer.addImport(BigDecimal.class);
                javaBuffer.addLine("public %1$s %2$s(BigDecimal new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
            else if (type == model.getStringType()) {
                // Add one method that takes "String" as parameter:
                javaBuffer.addLine("public %1$s %2$s(String new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
            else if (type == model.getDateType()) {
                // Add one method that takes "Date" as parameter:
                javaBuffer.addImport(Date.class);
                javaBuffer.addLine("public %1$s %2$s(Date new%3$s) {", thisName.getSimpleName(), field, property);
                javaBuffer.addLine(  "if (new%1$s == null) {", property);
                javaBuffer.addLine(    "%1$s = null;", field);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "else {");
                javaBuffer.addLine(    "%1$s = new Date(new%2$s.getTime());", field, property);
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
        }
        else if (type instanceof EnumType) {
            javaBuffer.addLine("public %1$s %2$s(%3$s new%4$s) {", thisName.getSimpleName(), field,
                typeReference.getText(), property);
            javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
            javaBuffer.addLine(  "return this;");
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }
        else if (type instanceof StructType) {
            JavaClassName builderName = javaTypes.getBuilderName(type);
            javaBuffer.addImport(builderName);

            // Add one method that takes the interface as parameter:
            javaBuffer.addLine("public %1$s %3$s(%2$s new%4$s) {", thisName.getSimpleName(), typeReference.getText(),
                field, property);
            javaBuffer.addLine(  "%1$s = new%2$s;", field, property);
            javaBuffer.addLine(  "return this;");
            javaBuffer.addLine("}");
            javaBuffer.addLine();

            // Add one method that takes a builder as parameter:
            javaBuffer.addLine("public %1$s %3$s(%2$s new%4$s) {", thisName.getSimpleName(),
                builderName.getSimpleName(), field, property);
            javaBuffer.addLine(  "if (new%1$s == null) {", property);
            javaBuffer.addLine(    "%1$s = null;", field);
            javaBuffer.addLine(  "}");
            javaBuffer.addLine(  "else {");
            javaBuffer.addLine(    "%1$s = new%2$s.build();", field, property);
            javaBuffer.addLine(  "}");
            javaBuffer.addLine(  "return this;");
            javaBuffer.addLine("}");
            javaBuffer.addLine();
        }
        else if (type instanceof ListType) {
            ListType listType = (ListType) type;
            Type elementType = listType.getElementType();
            JavaTypeReference elementReference = javaTypes.getTypeReference(elementType, true);
            javaBuffer.addImports(elementReference.getImports());

            // Add one method that sets the list from another list of objects:
            javaBuffer.addImport(ArrayList.class);
            javaBuffer.addLine("public %1$s %3$s(%2$s new%4$s) {", thisName.getSimpleName(), typeReference.getText(),
                field, property);
            javaBuffer.addLine(  "if (new%1$s != null) {", property);
            javaBuffer.addLine(    "if (%1$s == null) {", field);
            javaBuffer.addLine(      "%1$s = new ArrayList<>(new%2$s);", field, property);
            javaBuffer.addLine(    "}");
            javaBuffer.addLine(    "else {");
            javaBuffer.addLine(      "%1$s.addAll(new%2$s);", field, property);
            javaBuffer.addLine(    "}");
            javaBuffer.addLine(  "}");
            javaBuffer.addLine(  "return this;");
            javaBuffer.addLine("}");
            javaBuffer.addLine();

            // Add one method that sets the list from an array:
            javaBuffer.addImport(ArrayList.class);
            javaBuffer.addImport(Collections.class);
            javaBuffer.addLine("public %1$s %3$s(%2$s... new%4$s) {", thisName.getSimpleName(),
                elementReference.getText(), field, property);
            javaBuffer.addLine(  "if (new%1$s != null) {", property);
            javaBuffer.addLine(    "if (%1$s == null) {", field);
            javaBuffer.addLine(      "%1$s = new ArrayList<>(new%2$s.length);", field, property);
            javaBuffer.addLine(    "}");
            javaBuffer.addLine(    "Collections.addAll(%1$s, new%2$s);", field, property);
            javaBuffer.addLine(  "}");
            javaBuffer.addLine(  "return this;");
            javaBuffer.addLine("}");
            javaBuffer.addLine();

            if (elementType instanceof StructType) {
                JavaClassName builderName = javaTypes.getBuilderName(elementType);
                javaBuffer.addImport(builderName);

                // Add a method that sets the list from an array of builders:
                javaBuffer.addImport(ArrayList.class);
                javaBuffer.addLine("public %1$s %3$s(%2$s... new%4$s) {", thisName.getSimpleName(),
                    builderName.getSimpleName(), field, property);
                javaBuffer.addLine(  "if (new%1$s != null) {", property);
                javaBuffer.addLine(    "if (%1$s == null) {", field);
                javaBuffer.addLine(      "%1$s = new ArrayList<>(new%2$s.length);", field, property);
                javaBuffer.addLine(    "}");
                javaBuffer.addLine(    "for (%1$s builder : new%2$s) {", builderName.getSimpleName(), property);
                javaBuffer.addLine(      "%1$s.add(builder.build());", field);
                javaBuffer.addLine(    "}");
                javaBuffer.addLine(  "}");
                javaBuffer.addLine(  "return this;");
                javaBuffer.addLine("}");
                javaBuffer.addLine();
            }
        }
        javaBuffer.addLine();
    }

    private void generateBuildersFactory(Model model) {
        Name name = NameParser.parseUsingCase("Builders");
        javaBuffer = new JavaClassBuffer();
        JavaClassName factoryName = new JavaClassName();
        factoryName.setPackageName(javaPackages.getBuildersPackageName());
        factoryName.setSimpleName(javaNames.getJavaClassStyleName(name));
        javaBuffer.setClassName(factoryName);
        generateBuildersFactorySource(model);
        try {
            javaBuffer.write(outDir);
        }
        catch (IOException exception) {
            throw new RuntimeException("Can't write file for builder factory \"" + factoryName + "\"", exception);
        }
    }

    private void generateBuildersFactorySource(Model model) {
        // Begin class:
        javaBuffer.addLine("public class %1$s {", javaBuffer.getClassName().getSimpleName());
        javaBuffer.addLine();

        // Generate the builders method for each type:
        model.types()
            .filter(StructType.class::isInstance)
            .map(StructType.class::cast)
            .sorted()
            .forEach(this::generateBuilderFactoryMethods);

        // End class:
        javaBuffer.addLine("}");
    }

    private void generateBuilderFactoryMethods(StructType type) {
        JavaClassName builderName = javaTypes.getBuilderName(type);
        javaBuffer.addImport(builderName);
        String methodName = javaNames.getJavaMemberStyleName(type.getName());
        javaBuffer.addLine("public static %1$s %2$s() {", builderName.getSimpleName(), methodName);
        javaBuffer.addLine(  "return new %1$s();", builderName.getSimpleName());
        javaBuffer.addLine("}");
        javaBuffer.addLine();
    }
}
