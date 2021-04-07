/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.api.metamodel.analyzer;

import java.util.List;
import java.util.Stack;

import org.ovirt.api.metamodel.concepts.ArrayExpression;
import org.ovirt.api.metamodel.concepts.EnumType;
import org.ovirt.api.metamodel.concepts.Expression;
import org.ovirt.api.metamodel.concepts.ListType;
import org.ovirt.api.metamodel.concepts.MemberInvolvementTree;
import org.ovirt.api.metamodel.concepts.Name;
import org.ovirt.api.metamodel.concepts.NameParser;
import org.ovirt.api.metamodel.concepts.Parameter;
import org.ovirt.api.metamodel.concepts.PrimitiveType;
import org.ovirt.api.metamodel.concepts.StructType;
import org.ovirt.api.metamodel.concepts.Type;

public class InputDetailAnalyzer {

    //In 'live documentation' certain words are keywords that are searched for.
    //Theses words are defined here as constants.
    private static final Name OPTIONAL = NameParser.parseUsingCase("Optional");
    private static final Name MANDATORY = NameParser.parseUsingCase("Mandatory");
    private static final Name COLLECTION = NameParser.parseUsingCase("Collection");
    private static final Name OR = NameParser.parseUsingCase("Or");

    /**
     * Analyze 'live documentation' code and make updates to the provided Parameters.
     */
    public void analyzeInput(String sourceCode, List<Parameter> parameters) {
        ExpressionAnalyzer expressionAnalyzer = new ExpressionAnalyzer();
        List<Expression> expressions = expressionAnalyzer.analyzeExpressions(sourceCode);
        for (Expression expression : expressions) {
            assert expression instanceof MethodExpression;
            analyzeExpression((MethodExpression)expression, parameters);
        }
    }

    /**
     * Analyze a single 'live documentation' expression. Each expression represents an attribute.
     * The attribute is added to the relevant Parameter.
     */
    private void analyzeExpression(MethodExpression expression, List<Parameter> parameters) {
        if (expression.getMethod().equals(OR)) {
            analyzeOrExpression(expression, parameters);
        } else {
            boolean mandatory = isMandatory(expression);
            expression = getMethodExpression(removePrefix(expression));
            analyzeExpression(expression, parameters, mandatory);
        }
    }

    /**
     * Receives an expression which may be a MethodExpression or an ArrayExpression.
     * In case it's a MethodExpression, returns is as is, casted. In case it's an
     * array expression, discards the index of the array and returns the rest of
     * the expression cased to MethodExpression.
     */
    private MethodExpression getMethodExpression(Expression expression) {
        return expression instanceof ArrayExpression ?
                (MethodExpression)(((ArrayExpression)expression).getArray())
                : (MethodExpression)expression;
    }

    /**
     * Determine whether the current expression represents a mandatory
     * (vs optional) attribute.
     */
    private boolean isMandatory(MethodExpression expression) {
        return expression.getMethod().equals(MANDATORY) ? true : false;
    }

    /**
     * The purpose of this method is to remove 'mandatory' or 'optional'
     * methods from the Expression, if they exist.
     *
     * For example, the output of this method for the expression:
     * mandatory(disk().format()) would be disk().format(). The output
     * for disk().format() would be disk().format() (unchanged)
     */
    private Expression removePrefix(MethodExpression expression) {
        return expression.getMethod().equals(MANDATORY) || expression.getMethod().equals(OPTIONAL) ?
                expression.getParameters().get(0) : expression;
    }

    /**
     * This method is expected to receive at input expressions of the form:
     * disk().quota().id() - meaning chained, parameterless method invocations.
     * The method finds the relevant Parameter among the provided ones and
     * updates the correct MemberInvolvementTree in it with information obtained
     * from the expression.
     */
        //when lines such as "disk().quota().id()" are made into MethodExpressions,
    private MemberInvolvementTree analyzeExpression(MethodExpression expression, List<Parameter> parameters, boolean mandatory) {
        //their elements (disk, quota, id) are reversed in order. A stack is used here
        //to restore the original order.
        Stack<Name> stack = new Stack<>();
        stackExpressionElements(stack, expression);

        //find the relevant parameter.
        Parameter parameter = getParameter(stack.pop(), parameters);

        //update the parameter.
        return updateParameter(stack, mandatory, parameter);
    }

    /**
     * Stack elements of the provided expression to reverse their order.
     * Collection elements are flagged with collection=true.
     */
    private void stackExpressionElements(Stack<Name> stack, MethodExpression expression) {
        stackExpressionElements(stack, expression, false);
    }

    private void stackExpressionElements(Stack<Name> stack, MethodExpression expression, boolean nextElementIsACollection) {
        if (expression.getMethod()!=null) {
            stack.push(expression.getMethod());
        }
        if (expression.getTarget()!=null) {
            if (expression.getTarget() instanceof MethodExpression) {
                //call this method recursively; stack the next element.
                stackExpressionElements(stack, (MethodExpression)expression.getTarget());
            } else {
                //stack a collection element.
                stackCollectionExpression(stack, expression);
            }
        }
    }

    /**
     * This method inserts a collection element into the stack. A collection element is an array
     * of the form: ...[COLLECTION]... The way that such an element is handled is:
     * 1) The array index ([COLLECTION]) is discarded.
     * 2) The stacking process continues for the rest of the expression.
     * (Note: the information that an element is a collection will be available later
     * by examining the element-type, that's why [COLLECTION] can be safely discarded).
     */
    private void stackCollectionExpression(Stack<Name> stack, MethodExpression expression) {
        if (expression.getTarget() instanceof ArrayExpression) {
            ArrayExpression arrayExpression = (ArrayExpression)expression.getTarget();
            if (!(arrayExpression.getIndex() instanceof FieldExpression)
                    || !((FieldExpression)arrayExpression.getIndex()).getField().equals(COLLECTION))  {
                throw new IllegalArgumentException("The only valid array expression in live documentation is [COLLECTION]");
            }
            else {  //A valid collection expression
                stackExpressionElements(stack, (MethodExpression)arrayExpression.getArray(), true);
            }
        }
        else {
            throw new IllegalArgumentException("Can't handle " + expression.getClass().getSimpleName()
                   + " in this context; only MethodExpression or ArrayExpression");
        }
    }

    private MemberInvolvementTree updateParameter(Stack<Name> stack, boolean mandatory, Parameter parameter) {
        //handle the special case of a single-element expression, e.g: "fenceType()".
        //Such expressions are only expected to appear in the live documentation of
        //Actions, and they represent primitive (integer, boolean, String) or enum types.
        //These types don't have a member-involvement-tree associated with them in the
        //Parameter object, and whether or not they are mandatory is expressed by the
        //Parameter.mandatory attribute.
        if (stack.isEmpty()) {
            assert parameter.getType() instanceof PrimitiveType || parameter.getType() instanceof EnumType;
            parameter.setMandatory(mandatory);
            return null;
        } else {
            //find the member-involvement-tree which should be updated.
            MemberInvolvementTree currentNode = getMemberTree(parameter, stack.pop());
            if (currentNode.getType()==null) {
                currentNode.setType(getType(parameter.getType(), currentNode.getName()));
            }
            while (!stack.isEmpty()) {
                Name name = stack.pop();
                MemberInvolvementTree nextNode = currentNode.getNode(name);
                if (nextNode==null) {
                    nextNode = new MemberInvolvementTree(name, currentNode);
                    currentNode.getNodes().add(nextNode);
                }
                if (nextNode.getType()==null) {
                    nextNode.setType(getType(currentNode.getType(), nextNode.getName()));
                }
                currentNode = nextNode;
            }
            //stack has been emptied so this is a leaf. Set 'mandatory' (true/false).
            currentNode.setMandatory(mandatory);
            return currentNode;
        }
    }

    private Parameter getParameter(Name name, List<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            if (parameter.getName().equals(name)) {
                return parameter;
            }
        }
        throw new IllegalStateException("A parameter by name " + name + " was expected to be found.");
    }

    /**
     * Find the member-involvement-tree with the provided name and return it.
     * If no such tree exist, create it and add it to the Parameter object,
     * then return it.
     */
    private MemberInvolvementTree getMemberTree(Parameter parameter, Name name) {
        for (MemberInvolvementTree tree : parameter.getMemberInvolvementTrees()) {
            if (tree.getName().equals(name)) {
                return tree;
            }
        }
        //if none found, create a new one, add it to the Parameter and return it
        MemberInvolvementTree tree = new MemberInvolvementTree(name);
        parameter.getMemberInvolvementTrees().add(tree);
        return tree;
    }

    private Type getType(Type type, Name name) {
        //all nodes except for leaves in MemberInvovlementTree are assumed
        //to be a StructType or ListType (for collections)
        if (type instanceof StructType) {
            return ((StructType)type).getMember(name).get().getType();
        }
        else if (type instanceof ListType) {
            ListType listType = (ListType)type;
            //use the element-type of the list
            StructType structType = (StructType)listType.getElementType();
            return structType.getMember(name).get().getType();
        }
        else {
            throw new IllegalArgumentException("Expected StructType or ListType element");
        }
    }

    /**
     * Analyzes a live documentation expression which includes an 'or' operator.
     * Known limitations: 1) Only binary 'or' supported (or(a,b,c) not supported).
     * 2) no current way to express 'or' relationship between a primitive parameter
     * (e.g: fenceType()) and a complex parameter (e.g: host().address()). This is
     * because primitive parameters are not expressed using member-involvement-trees,
     * and the current method of expression 'or' is by using member-involvement-trees.
     */
    private void analyzeOrExpression(MethodExpression expression, List<Parameter> parameters) {
        List<Expression> expressions = expression.getParameters();
        assert (expressions.size()==2); //binary 'or'.
        assert (expressions.get(0) instanceof MethodExpression); //expected to start with: "mandatory|optional(...)
        assert (expressions.get(1) instanceof MethodExpression); //expected to start with: "mandatory|optional(...)
        boolean mandatory = isMandatory((MethodExpression)expressions.get(0));
        //if one is mandatory, the other is also expected to be, and vice-versa.
        assert mandatory==isMandatory((MethodExpression)expressions.get(1));
        MethodExpression expression1 = getMethodExpression(removePrefix((MethodExpression)expressions.get(0)));
        MethodExpression expression2 = getMethodExpression(removePrefix((MethodExpression)expressions.get(1)));
        MemberInvolvementTree tree1 = analyzeExpression(expression1, parameters, mandatory);
        MemberInvolvementTree tree2 = analyzeExpression(expression2, parameters, mandatory);
        //Make the two leaves which have an 'or' relationship between them 'point' to each other.
        tree1.setAlternative(tree2);
        assert (!tree2.hasChildren());
        removeTree(tree2, parameters, expression2);
    }

    /**
     * Removes the provided node from either the tree it exists in, or,
     * if this node is a root - from the parent parameter.
     */
    private void removeTree(MemberInvolvementTree tree, List<Parameter> parameters, MethodExpression expression) {
        if (tree.hasParent()) {
            tree.cutSelf();
        } else {
            //to restore the original order.
            Stack<Name> stack = new Stack<>();
            stackExpressionElements(stack, expression);

            //find the relevant parameter.
            Parameter parameter = getParameter(stack.pop(), parameters);
            parameter.removeMemeberInvolvementTree(tree.getName());
        }
    }

}
