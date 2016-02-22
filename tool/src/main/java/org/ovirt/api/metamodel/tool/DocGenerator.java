/*
Copyright (c) 2016 Red Hat, Inc.

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

import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.ovirt.api.metamodel.concepts.Attribute;
import org.ovirt.api.metamodel.concepts.Concept;
import org.ovirt.api.metamodel.concepts.EnumType;
import org.ovirt.api.metamodel.concepts.EnumValue;
import org.ovirt.api.metamodel.concepts.Link;
import org.ovirt.api.metamodel.concepts.ListType;
import org.ovirt.api.metamodel.concepts.Method;
import org.ovirt.api.metamodel.concepts.Model;
import org.ovirt.api.metamodel.concepts.Parameter;
import org.ovirt.api.metamodel.concepts.PrimitiveType;
import org.ovirt.api.metamodel.concepts.Service;
import org.ovirt.api.metamodel.concepts.StructType;
import org.ovirt.api.metamodel.concepts.Type;

/**
 * This class takes a model and generates the corresponding AsciiDoc documentation.
 */
@ApplicationScoped
public class DocGenerator {
    // Reference to the object used to calculate names:
    @Inject private Words words;

    // The directory were the output will be generated:
    private File outDir;

    // The buffer used to generate the AsciiDoc source code:
    private AsciiDocBuffer docBuffer;

    /**
     * Set the directory were the output will be generated.
     */
    public void setOutDir(File newOutDir) {
        outDir = newOutDir;
    }

    public void generate(Model model) {
        // Generate the AscciiDoc file:
        docBuffer = new AsciiDocBuffer();
        docBuffer.setName("model");
        documentModel(model);
        try {
            docBuffer.write(outDir);
        }
        catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void documentModel(Model model) {
        // Introduction:
        docBuffer.addLine("= Model");
        docBuffer.addLine();

        // Services:
        docBuffer.addId("services");
        docBuffer.addLine("== Services");
        docBuffer.addLine();
        model.services().sorted().forEach(this::documentService);

        // Types:
        docBuffer.addId("types");
        docBuffer.addLine("== Types");
        docBuffer.addLine();
        model.types().sorted().forEach(this::documentType);
    }

    private void documentService(Service service) {
        // General description:
        docBuffer.addId(getId(service));
        docBuffer.addLine("=== %s", getName(service));
        docBuffer.addLine();
        addDoc(service);

        // Table of methods:
        List<Method> methods = service.getMethods();
        if (!methods.isEmpty()) {
            docBuffer.addLine(".Methods summary (%d)", methods.size());
            docBuffer.addLine("[cols=\"20,80\"]");
            docBuffer.addLine("|===");
            docBuffer.addLine("|Name |Summary");
            docBuffer.addLine();
            methods.stream().sorted().forEach(method -> {
                docBuffer.addLine("|%s", getName(method));
                docBuffer.addLine("|%s", getSummary(method));
                docBuffer.addLine();
            });
            docBuffer.addLine("|===");
            docBuffer.addLine();
        }

        // Methods detail:
        methods.stream().sorted().forEach(this::documentMethod);
    }

    private void documentMethod(Method method) {
        // General description:
        docBuffer.addId(getId(method));
        docBuffer.addLine("==== %s", getName(method));
        docBuffer.addLine();
        addDoc(method);

        // Table of parameters:
        List<Parameter> parameters = method.getParameters();
        if (!parameters.isEmpty()) {
            docBuffer.addLine(".Parameters summary (%d)", parameters.size());
            docBuffer.addLine("[cols=\"15,15,10,60\"]");
            docBuffer.addLine("|===");
            docBuffer.addLine("|Name |Type |Direction |Summary");
            docBuffer.addLine();
            parameters.stream().sorted().forEach(parameter -> {
                docBuffer.addLine("|%s", getName(parameter));
                docBuffer.addLine("|%s", getLink(parameter.getType()));
                docBuffer.addLine("|%s", getDirection(parameter));
                docBuffer.addLine("|%s", getSummary(parameter));
                docBuffer.addLine();
            });
            docBuffer.addLine("|===");
            docBuffer.addLine();
        }

        // Detail of parameters:
        parameters.stream().sorted().forEach(this::documentParameter);
    }

    private void documentParameter(Parameter parameter) {
        String doc = parameter.getDoc();
        if (doc != null) {
            docBuffer.addId(getId(parameter));
            docBuffer.addLine("==== %s", getName(parameter));
            docBuffer.addLine();
            addDoc(parameter);
        }
    }

    private void documentType(Type type) {
        if (type instanceof PrimitiveType) {
            documentPrimitive((PrimitiveType) type);
        }
        if (type instanceof EnumType) {
            documentEnum((EnumType) type);
        }
        else if (type instanceof StructType) {
            documentStruct((StructType) type);
        }
    }

    private void documentPrimitive(PrimitiveType type) {
        // General description:
        docBuffer.addId(getId(type));
        docBuffer.addLine("=== %s [small]#primitive#", getName(type));
        docBuffer.addLine();
        addDoc(type);
    }

    private void documentEnum(EnumType type) {
        // General description:
        docBuffer.addId(getId(type));
        docBuffer.addLine("=== %s [small]#enum#", getName(type));
        docBuffer.addLine();
        addDoc(type);

        // Table of values:
        List<EnumValue> values = type.getValues();
        if (!values.isEmpty()) {
            docBuffer.addLine(".Values summary (%d)", values.size());
            docBuffer.addLine("[cols=\"20,89\"]");
            docBuffer.addLine("|===");
            docBuffer.addLine("|Name |Summary");
            docBuffer.addLine();
            values.stream().sorted().forEach(value -> {
                docBuffer.addLine("|%s", getName(value));
                docBuffer.addLine("|%s", getSummary(value));
                docBuffer.addLine();
            });
            docBuffer.addLine("|===");
            docBuffer.addLine();
        }

        // Detail of values:
        values.stream().sorted().forEach(this::documentValue);
    }

    private void documentValue(EnumValue value) {
        String doc = value.getDoc();
        if (doc != null) {
            docBuffer.addId(getId(value));
            docBuffer.addLine("==== %s", getName(value));
            docBuffer.addLine();
            addDoc(value);
        }
    }

    private void documentStruct(StructType type) {
        // General description:
        docBuffer.addId(getId(type));
        docBuffer.addLine("=== %s [small]#struct#", getName(type));
        docBuffer.addLine();
        addDoc(type);

        // Table of attributes:
        List<Attribute> attributes = type.getAttributes();
        if (!attributes.isEmpty()) {
            docBuffer.addLine(".Attributes summary (%d)", attributes.size());
            docBuffer.addLine("[cols=\"20,20,60\"]");
            docBuffer.addLine("|===");
            docBuffer.addLine("|Name |Type |Summary");
            docBuffer.addLine();
            attributes.stream().sorted().forEach(attribute -> {
                docBuffer.addLine("|%s", getName(attribute));
                docBuffer.addLine("|%s", getLink(attribute.getType()));
                docBuffer.addLine("|%s", getSummary(attribute));
                docBuffer.addLine();
            });
            docBuffer.addLine("|===");
            docBuffer.addLine();
        }

        // Detail of attributes:
        attributes.stream().sorted().forEach(this::documentAttribute);

        // Table of links:
        List<Link> links = type.getLinks();
        if (!links.isEmpty()) {
            docBuffer.addLine(".Links summary (%d)", links.size());
            docBuffer.addLine("[cols=\"20,20,60\"]");
            docBuffer.addLine("|===");
            docBuffer.addLine("|Name |Type |Summary");
            docBuffer.addLine();
            links.stream().sorted().forEach(link -> {
                docBuffer.addLine("|%s", getName(link));
                docBuffer.addLine("|%s", getLink(link.getType()));
                docBuffer.addLine("|%s", getSummary(link));
                docBuffer.addLine();
            });
            docBuffer.addLine("|===");
            docBuffer.addLine();
        }

        // Detail of links:
        links.stream().sorted().forEach(this::documentLink);
    }

    private void documentAttribute(Attribute attribute) {
        String doc = attribute.getDoc();
        if (doc != null) {
            docBuffer.addId(getId(attribute));
            docBuffer.addLine("==== %s", getName(attribute));
            docBuffer.addLine();
            addDoc(attribute);
        }
    }

    private void documentLink(Link link) {
        String doc = link.getDoc();
        if (doc != null) {
            docBuffer.addId(getId(link));
            docBuffer.addLine("==== %s", getName(link));
            docBuffer.addLine();
            addDoc(link);
        }
    }

    private String getName(Concept concept) {
        return concept.getName().words().map(words::capitalize).collect(joining());
    }

    private String getSummary(Concept concept) {
        String doc = concept.getDoc();
        if (doc != null) {
            int index = doc.indexOf('.');
            if (index != -1) {
                return doc.substring(0, index);
            }
            return doc;
        }
        return "";
    }

    private String getDirection(Parameter parameter) {
        if (parameter.isIn() && parameter.isOut()) {
            return "In/Out";
        }
        if (parameter.isIn()) {
            return "In";
        }
        if (parameter.isOut()) {
            return "Out";
        }
        return "";
    }

    private String getId(Type type) {
        return "types/" + type.getName();
    }

    private String getId(Service service) {
        return "services/" + service.getName();
    }

    private String getId(Attribute attribute) {
        return getId(attribute.getDeclaringType()) + "/attributes/" + attribute.getName();
    }

    private String getId(Link link) {
        return getId(link.getDeclaringType()) + "/links/" + link.getName();
    }

    private String getId(Method method) {
        return getId(method.getDeclaringService()) + "/methods/" + method.getName();
    }

    private String getId(Parameter parameter) {
        return getId(parameter.getDeclaringMethod()) + "/parameters/" + parameter.getName();
    }

    private String getId(EnumValue value) {
        return getId(value.getDeclaringType()) + "/values/" + value.getName();
    }

    private String getLink(Type type) {
        String id = null;
        String text = null;
        if (type instanceof PrimitiveType || type instanceof EnumType || type instanceof StructType) {
            id = getId(type);
            text = getName(type);
        }
        else if (type instanceof ListType) {
            ListType listType = (ListType) type;
            Type elementType = listType.getElementType();
            id = getId(elementType);
            text = getName(elementType) + "[]";
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append("<<");
        buffer.append(id);
        if (text != null) {
            buffer.append(",");
            buffer.append(text);
        }
        buffer.append(">>");
        return buffer.toString();
    }

    private void addDoc(Concept concept) {
        List<String> lines = new ArrayList<>();
        String doc = concept.getDoc();
        if (doc != null) {
            Collections.addAll(lines, doc.split("\n"));
        }
        lines.stream().forEach(docBuffer::addLine);
        docBuffer.addLine();
    }
}
