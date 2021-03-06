/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.compiler.java.codegen;

import static com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.JT_CLASS_NEW;
import static com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.JT_EXTENDS;
import static com.redhat.ceylon.compiler.java.codegen.AbstractTransformer.JT_NO_PRIMITIVES;

import java.util.ArrayList;

import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

public class CallableBuilder {

    private final AbstractTransformer gen;
    private ProducedType typeModel;
    private List<JCStatement> body;
    private ParameterList paramLists;
    private Tree.ParameterList parameterListTree;
    private Term forwardCallTo;
    
    private CallableBuilder(CeylonTransformer gen) {
        this.gen = gen;
    }
    
    /**
     * Constructs an {@code AbstractCallable} suitable for wrapping a method reference.
     */
    public static CallableBuilder methodReference(CeylonTransformer gen, Tree.Term expr, ParameterList parameterList) {
        CallableBuilder cb = new CallableBuilder(gen);
        cb.paramLists = parameterList;
        cb.typeModel = expr.getTypeModel();
        cb.forwardCallTo = expr;
        return cb;
    }
    
    /**
     * Constructs an {@code AbstractCallable} suitable for an anonymous function.
     * @param parameterList2 
     */
    public static CallableBuilder anonymous(
            CeylonTransformer gen, Tree.Expression expr, ParameterList parameterList, 
            Tree.ParameterList parameterListTree, 
            ProducedType callableTypeModel) {
        JCExpression transformedExpr = gen.expressionGen().transformExpression(expr);
        final List<JCStatement> stmts = List.<JCStatement>of(gen.make().Return(transformedExpr));
        return methodArgument(gen, callableTypeModel, parameterList, parameterListTree, stmts);
    }

    public static CallableBuilder methodArgument(
            CeylonTransformer gen,
            ProducedType callableTypeModel,
            ParameterList parameterList,
            Tree.ParameterList parameterListTree, 
            List<JCStatement> stmts) {
        
        CallableBuilder cb = new CallableBuilder(gen);
        cb.paramLists = parameterList;
        cb.typeModel = callableTypeModel;
        cb.body = stmts;
        cb.parameterListTree = parameterListTree;
        return cb;
    }
    
    /**
     * Constructs an {@code AbstractCallable} suitable for use in a method 
     * definition with a multiple parameter list.
     */
    public static CallableBuilder mpl(
            CeylonTransformer gen,
            ProducedType typeModel,
            ParameterList parameterList,
            Tree.ParameterList parameterListTree,
            List<JCStatement> body) {

        CallableBuilder cb = new CallableBuilder(gen);
        cb.paramLists = parameterList;
        cb.typeModel = typeModel;
        if (body == null) {
            body = List.<JCStatement>nil();
        }
        cb.body = body;
        cb.parameterListTree = parameterListTree;
        return cb;
    }

    public JCNewClass build() {
        // Generate a subclass of Callable
        ListBuffer<JCTree> classBody = new ListBuffer<JCTree>();
        int numParams = paramLists.getParameters().size();
        int minimumParams = 0;
        for(Parameter p : paramLists.getParameters()){
            if(p.isDefaulted() || p.isSequenced())
                break;
            minimumParams++;
        }
        boolean isVariadic = minimumParams != numParams;
        if(parameterListTree != null){
            // generate a method for each defaulted param
            for(Tree.Parameter p : parameterListTree.getParameters()){
                if(p.getDefaultArgument() != null || p.getDeclarationModel().isSequenced()){
                    MethodDefinitionBuilder methodBuilder = gen.classGen().makeParamDefaultValueMethod(false, null, parameterListTree, p);
                    classBody.append(methodBuilder.build());
                }
            }
        }

        // collect each parameter type from the callable type model rather than the declarations to get them all bound
        java.util.List<ProducedType> parameterTypes = new ArrayList<ProducedType>(numParams);
        if(forwardCallTo != null){
            for(int i=0;i<numParams;i++)
                parameterTypes.add(gen.getParameterTypeOfCallable(typeModel, i));
        }else{
            // get them from our declaration
            for(Parameter p : paramLists.getParameters())
                parameterTypes.add(p.getType());
        }
            
        // now generate a method for each supported minimum number of parameters below 4
        // which delegates to the $call$typed method if required
        for(int i=minimumParams,max = Math.min(numParams,4);i<max;i++){
            classBody.append(makeDefaultedCall(i, isVariadic, parameterTypes));
        }
        // generate the $call method for the max number of parameters,
        // which delegates to the $call$typed method if required
        classBody.append(makeDefaultedCall(numParams, isVariadic, parameterTypes));
        // generate the $call$typed method if required
        if(isVariadic && forwardCallTo == null)
            classBody.append(makeCallTypedMethod(body, parameterTypes));
        
        JCClassDecl classDef = gen.make().AnonymousClassDef(gen.make().Modifiers(0), classBody.toList());
        
        JCNewClass instance = gen.make().NewClass(null, 
                null, 
                gen.makeJavaType(typeModel, JT_EXTENDS | JT_CLASS_NEW), 
                List.<JCExpression>of(gen.make().Literal(typeModel.getProducedTypeName(true))),
                classDef);
        return instance;
    }
    
    private JCExpression getTypedParameter(Parameter param, int argIndex, boolean varargs, java.util.List<ProducedType> parameterTypes){
        JCExpression argExpr;
        if (!varargs) {
            // The Callable has overridden one of the non-varargs call() 
            // methods
            argExpr = gen.make().Ident(
                    makeParamName(gen, argIndex));
        } else {
            // The Callable has overridden the varargs call() method
            // so we need to index into the varargs array
            argExpr = gen.make().Indexed(
                    gen.make().Ident(makeParamName(gen, 0)), 
                    gen.make().Literal(argIndex));
        }
        // make sure we unbox it if required
        argExpr = gen.expressionGen().applyErasureAndBoxing(argExpr, 
                parameterTypes.get(argIndex), // it came in as Object, but we need to pretend its type
                // is the parameter type because that's how unboxing determines how it has to unbox
                true, // it's completely erased
                true, // it came in boxed
                CodegenUtil.getBoxingStrategy(param), // see if we need to box 
                parameterTypes.get(argIndex), // see what type we need
                ExpressionTransformer.EXPR_DOWN_CAST); // we're effectively downcasting it from Object
        return argExpr;
    }
    
    private JCTree makeDefaultedCall(int i, boolean isVariadic, java.util.List<ProducedType> parameterTypes) {
        // collect every parameter
        int a = 0;
        ListBuffer<JCStatement> stmts = new ListBuffer<JCStatement>();
        for(Parameter param : paramLists.getParameters()){
            // don't read default parameter values for forwarded calls
            if(forwardCallTo != null && i == a)
                break;
            // read the value
            JCExpression paramExpression = getTypedParameter(param, a, i>3, parameterTypes);
            JCExpression varInitialExpression;
            if(param.isDefaulted() || param.isSequenced()){
                if(i > 3){
                    // must check if it's defined
                    JCExpression test = gen.make().Binary(JCTree.GT, gen.makeSelect(getParamName(0), "length"), gen.makeInteger(a));
                    JCExpression elseBranch = makeDefaultValueCall(param, a);
                    varInitialExpression = gen.make().Conditional(test, paramExpression, elseBranch);
                }else if(a >= i){
                    // get its default value because we don't have it
                    varInitialExpression = makeDefaultValueCall(param, a);
                }else{
                    // we must have it
                    varInitialExpression = paramExpression;
                }
            }else{
                varInitialExpression = paramExpression;
            }
            // store it in a local var
            int flags = 0;
            if(!CodegenUtil.isUnBoxed(param)){
                flags |= AbstractTransformer.JT_NO_PRIMITIVES;
            }
            // Always go raw if we're forwarding, because we're building the call ourselves and we don't get a chance to apply erasure and
            // casting to parameter expressions when we pass them to the forwarded method. Ideally we could set it up correctly so that
            // the proper erasure is done when we read from the Callable.call Object param, but since we store it in a variable defined here,
            // we'd need to duplicate some of the erasure logic here to make or not the type raw, and that would be worse.
            // Besides, named parameter invocation does the same.
            // See https://github.com/ceylon/ceylon-compiler/issues/1005
            if(forwardCallTo != null)
                flags |= AbstractTransformer.JT_RAW;
            JCStatement var = gen.make().VarDef(gen.make().Modifiers(Flags.FINAL), 
                    gen.naming.makeUnquotedName(getCallableTempVarName(param)), 
                    gen.makeJavaType(parameterTypes.get(a), flags),
                    varInitialExpression);
            stmts.append(var);
            a++;
        }
        if(forwardCallTo != null){
            final Tree.MemberOrTypeExpression primary;
            if (forwardCallTo instanceof Tree.MemberOrTypeExpression) {
                primary = (Tree.MemberOrTypeExpression)forwardCallTo;
            } else {
                throw new RuntimeException(forwardCallTo+"");
            }
            TypeDeclaration primaryDeclaration = forwardCallTo.getTypeModel().getDeclaration();
            CallableInvocation invocationBuilder = new CallableInvocation (
                    gen,
                    primary,
                    primaryDeclaration,
                    primary.getTarget(),
                    gen.getReturnTypeOfCallable(forwardCallTo.getTypeModel()),
                    forwardCallTo, 
                    paramLists,
                    i);
            boolean prevCallableInv = gen.expressionGen().withinCallableInvocation(true);
            try {
                stmts.append(gen.make().Return(gen.expressionGen().transformInvocation(invocationBuilder)));
            } finally {
                gen.expressionGen().withinCallableInvocation(prevCallableInv);
            }
        }else if(isVariadic){
            // chain to n param typed method
            List<JCExpression> args = List.nil();
            // pass along the parameters
            for(a=paramLists.getParameters().size()-1;a>=0;a--){
                Parameter param = paramLists.getParameters().get(a);
                args = args.prepend(gen.makeUnquotedIdent(getCallableTempVarName(param)));
            }
            JCMethodInvocation chain = gen.make().Apply(null, gen.makeUnquotedIdent(Naming.getCallableTypedMethodName()), args);
            stmts.append(gen.make().Return(chain));
        }else{
            // insert the method body directly
            stmts.appendList(this.body);
        }
        List<JCStatement> body = stmts.toList();
        return makeCallMethod(body, i);
    }

    private String getCallableTempVarName(Parameter param) {
        // prefix them with $$ if we only forward, otherwise we need them to have the proper names
        return forwardCallTo != null ? Naming.getCallableTempVarName(param) : param.getName();
    }

    private JCExpression makeDefaultValueCall(Parameter defaultedParam, int i){
        // add the default value
        List<JCExpression> defaultMethodArgs = List.nil();
        // pass all the previous values
        for(int a=i-1;a>=0;a--){
            Parameter param = paramLists.getParameters().get(a);
            JCExpression previousValue = gen.makeUnquotedIdent(getCallableTempVarName(param));
            defaultMethodArgs = defaultMethodArgs.prepend(previousValue);
        }
        // now call the default value method
        return gen.make().Apply(null, gen.makeUnquotedIdent(Naming.getDefaultedParamMethodName(null, defaultedParam)), defaultMethodArgs);
    }
    
    private JCTree makeCallMethod(List<JCStatement> body, int numParams) {
        MethodDefinitionBuilder callMethod = MethodDefinitionBuilder.callable(gen);
        callMethod.isOverride(true);
        callMethod.modifiers(Flags.PUBLIC);
        ProducedType returnType = gen.getReturnTypeOfCallable(typeModel);
        callMethod.resultType(gen.makeJavaType(returnType, JT_NO_PRIMITIVES), null);
        // Now append formal parameters
        switch (numParams) {
        case 3:
            callMethod.parameter(makeCallableCallParam(0, numParams-3));
            // fall through
        case 2:
            callMethod.parameter(makeCallableCallParam(0, numParams-2));
            // fall through
        case 1:
            callMethod.parameter(makeCallableCallParam(0, numParams-1));
            break;
        case 0:
            break;
        default: // use varargs
            callMethod.parameter(makeCallableCallParam(Flags.VARARGS, 0));
        }
        
        // Return the call result, or null if a void method
        callMethod.body(body);
        return callMethod.build();
    }

    private JCTree makeCallTypedMethod(List<JCStatement> body, java.util.List<ProducedType> parameterTypes) {
        // make the method
        MethodDefinitionBuilder methodBuilder = MethodDefinitionBuilder.systemMethod(gen, Naming.getCallableTypedMethodName());
        methodBuilder.ignoreAnnotations(false).noAnnotations();
        methodBuilder.modifiers(Flags.PRIVATE);
        ProducedType returnType = gen.getReturnTypeOfCallable(typeModel);
        methodBuilder.resultType(gen.makeJavaType(returnType, JT_NO_PRIMITIVES), null);
        // add all parameters
        int i=0;
        for(Parameter param : paramLists.getParameters()){
            ParameterDefinitionBuilder parameterBuilder = ParameterDefinitionBuilder.instance(gen, param.getName());
            JCExpression paramType = gen.makeJavaType(parameterTypes.get(i));
            parameterBuilder.type(paramType, null);
            methodBuilder.parameter(parameterBuilder);
            i++;
        }
        // Return the call result, or null if a void method
        methodBuilder.body(body);
        return methodBuilder.build();
    }

    private static Name makeParamName(AbstractTransformer gen, int paramIndex) {
        return gen.names().fromString(getParamName(paramIndex));
    }

    private static String getParamName(int paramIndex) {
        return "$param$"+paramIndex;
    }
    
    private ParameterDefinitionBuilder makeCallableCallParam(long flags, int ii) {
        JCExpression type = gen.makeIdent(gen.syms().objectType);
        if ((flags & Flags.VARARGS) != 0) {
            type = gen.make().TypeArray(type);
        }
        ParameterDefinitionBuilder pdb = ParameterDefinitionBuilder.instance(gen, getParamName(ii));
        pdb.modifiers(Flags.FINAL | flags);
        pdb.type(type, null);
        return pdb;
        /*
        return gen.make().VarDef(gen.make().Modifiers(Flags.FINAL | Flags.PARAMETER | flags), 
                makeParamName(gen, ii), type, null);
                */
    }
}
