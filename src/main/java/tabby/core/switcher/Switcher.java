package tabby.core.switcher;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import tabby.core.container.DataContainer;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.core.toolkit.PollutedVarsPointsToAnalysis;
import tabby.dal.caching.bean.edge.Call;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.util.PositionHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class Switcher {

    /**
     * @param context
     * @param dataContainer
     * @param method
     * @param methodRef
     */
    public static PollutedVarsPointsToAnalysis doMethodAnalysis(Context context,
                                                                DataContainer dataContainer,
                                                                SootMethod method,
                                                                MethodReference methodRef){
        try{
            if(method.isAbstract() || Modifier.isNative(method.getModifiers())
                    || method.isPhantom()){
                methodRef.setInitialed(true);
                methodRef.setActionInitialed(true);
                return null;
            }

            if(methodRef.isActionInitialed() && methodRef.isInitialed()){

                return null;
            }

            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            UnitGraph graph = new BriefUnitGraph(body);
            PollutedVarsPointsToAnalysis pta =
                    PollutedVarsPointsToAnalysis
                            .makeDefault(methodRef, body, graph,
                            dataContainer, context, !methodRef.isActionInitialed());

            methodRef.setInitialed(true);
            methodRef.setActionInitialed(true);
            return pta;
        }catch (RuntimeException e){
            e.printStackTrace();
        }
        return null;
    }



    public static TabbyVariable doInvokeExprAnalysis(
            Unit unit,
            InvokeExpr invokeExpr,
            DataContainer dataContainer,
            Context context){
        // extract baseVar and args
        TabbyVariable baseVar = Switcher.extractBaseVarFromInvokeExpr(invokeExpr, context);
        Map<Integer, TabbyVariable> args = Switcher.extractArgsFromInvokeExpr(invokeExpr, context);

        List<Integer> pollutedPosition = pollutedPositionAnalysis(baseVar, args, context);
        TabbyVariable firstPollutedVar = null;
        boolean flag = false;
        int index = 0;
        for(Integer pos:pollutedPosition){
            if(pos != PositionHelper.NOT_POLLUTED_POSITION){
                if(index == 0){
                    firstPollutedVar = baseVar;
                }else{
                    firstPollutedVar = args.get(index-1);
                }
                flag=true;
                break;
            }
            index++;
        }

        if(!flag) return null;
        // baseVar
        // find target MethodRef
        SootClass cls = invokeExpr.getMethod().getDeclaringClass();
        SootMethod invokedMethod = invokeExpr.getMethod();

        MethodReference methodRef = dataContainer
                .getOrAddMethodRef(invokeExpr.getMethodRef(), invokedMethod);

        // construct call edge
        String invokeType = "";
        if(invokeExpr instanceof StaticInvokeExpr){
            invokeType = "StaticInvoke";
        }else if(invokeExpr instanceof VirtualInvokeExpr){
            invokeType = "VirtualInvoke";
        }else if(invokeExpr instanceof SpecialInvokeExpr){
            invokeType = "SpecialInvoke";
        }else if(invokeExpr instanceof InterfaceInvokeExpr){
            invokeType = "InterfaceInvoke";
        }

        // try to analysis this method
        if((!methodRef.isInitialed() || !methodRef.isActionInitialed()) // never analysis with pta
                && !context.isInRecursion(methodRef.getSignature())){ // not recursion
            Context subContext = context.createSubContext(methodRef.getSignature(), methodRef);
            Switcher.doMethodAnalysis(subContext, dataContainer, invokedMethod, methodRef);
        }
        // 回溯
        TabbyVariable retVar = null;
        if("<init>".equals(methodRef.getName())
                && baseVar != null && !baseVar.isPolluted(-1)){
            for(TabbyVariable arg: args.values()){
                if(arg != null && arg.isPolluted(-1)){
                    baseVar.getValue().setPolluted(true);
                    baseVar.getValue().setRelatedType(arg.getValue().getRelatedType());
                    break;
                }
            }
        }

        for (Map.Entry<String, String> entry : methodRef.getActions().entrySet()) {
            String position = entry.getKey();
            String newRelated = entry.getValue();
            if("return".equals(position))continue;
            TabbyVariable oldVar = parsePosition(position, baseVar, args, true);
            TabbyVariable newVar = null;

            if (oldVar != null) {
                if ("clear".equals(newRelated)) {
                    oldVar.clearVariableStatus();
                } else {
                    boolean remain = false;
                    if(newRelated != null && newRelated.contains("&remain")){
                        remain = true;
                    }
                    newVar = parsePosition(newRelated, baseVar, args, false);
                    oldVar.assign(newVar, remain);
                }
            }
        }

        if(methodRef.getActions().containsKey("return")){
            retVar = parsePosition(methodRef.getActions().get("return"), baseVar, args, true);
        }
        boolean optimize = false;


        buildCallRelationship(cls.getName(), context, optimize,
                methodRef, dataContainer, unit, invokeType,
                pollutedPosition);

        return retVar;
    }

    public static List<Integer> pollutedPositionAnalysis(TabbyVariable baseVar,
                                                         Map<Integer, TabbyVariable> args,
                                                         Context context){
        List<Integer> positions = new ArrayList<>();
        // baseVar
        positions.add(getPollutedPosition(baseVar));

        // args
        for(TabbyVariable var: args.values()){
            positions.add(getPollutedPosition(var));
        }

        return positions;
    }

    public static int getPollutedPosition(TabbyVariable var){
        if(var != null){
            String related = null;
            if(var.isPolluted(-1)){ //
                related = var.getValue().getRelatedType();
            }else if(var.containsPollutedVar(new ArrayList<>())){ //
                related = var.getFirstPollutedVarRelatedType();
            }
            if(related != null){
                return PositionHelper.getPosition(related);
            }
        }
        return PositionHelper.NOT_POLLUTED_POSITION;
    }

    public static void buildCallRelationship(String classname, Context context, boolean isOptimize,
                                      MethodReference targetMethodRef, DataContainer dataContainer,
                                      Unit unit, String invokeType, List<Integer> pollutedPosition){
        MethodReference sourceMethodRef = context.getMethodReference();
        if(sourceMethodRef == null || targetMethodRef == null){

            return;
        }
        boolean isPolluted = true;
        if(targetMethodRef.isSink()){

            for(int i:targetMethodRef.getPollutedPosition()){
                if(pollutedPosition.size() > i+1 && pollutedPosition.get(i+1) == PositionHelper.NOT_POLLUTED_POSITION){
                    isPolluted = false;
                    break;
                }
            }
        }

        if(!targetMethodRef.isIgnore()
                && isPolluted){

            if("java.lang.String".equals(classname)
                    && ("equals".equals(targetMethodRef.getName())
                    || "hashCode".equals(targetMethodRef.getName())
                    || "length".equals(targetMethodRef.getName()))) return;

            if("java.lang.StringBuilder".equals(classname)
                    && ("toString".equals(targetMethodRef.getName())
                    || "hashCode".equals(targetMethodRef.getName()))) return;

            Call call = Call.newInstance(sourceMethodRef, targetMethodRef);
            call.setRealCallType(classname);
            call.setInvokerType(invokeType);
            call.setPollutedPosition(new ArrayList<>(pollutedPosition));
            call.setUnit(unit);
            call.setLineNum(unit.getJavaSourceStartLineNumber());
            if(!sourceMethodRef.getCallEdge().contains(call)){
                sourceMethodRef.getCallEdge().add(call);
                dataContainer.store(call);
            }

        }
    }

    public static TabbyVariable extractBaseVarFromInvokeExpr(InvokeExpr invokeExpr, Context context){
        TabbyVariable baseVar = null;
        List<ValueBox> valueBoxes = invokeExpr.getUseBoxes();
        for(ValueBox box:valueBoxes){
            Value value = box.getValue();
            if(box instanceof JimpleLocalBox){
                baseVar = context.getOrAdd(value);
                break;
            }
        }
        if(baseVar == null && invokeExpr instanceof SpecialInvokeExpr){
            baseVar = context.getOrAdd(context.getThisVar());
        }
        return baseVar;
    }

    public static Map<Integer, TabbyVariable> extractArgsFromInvokeExpr(InvokeExpr invokeExpr, Context context){
        Map<Integer, TabbyVariable> args = new HashMap<>();
        for(int i=0; i<invokeExpr.getArgCount(); i++){
            TabbyVariable var = context.getOrAdd(invokeExpr.getArg(i));
            args.put(i, var);
        }
        return args;
    }

    public static TabbyVariable parsePosition(String position,
                                              TabbyVariable baseVar,
                                              Map<Integer, TabbyVariable> args,
                                              boolean created){
        if(position == null) return null;
        TabbyVariable retVar = null;
        String[] positions = position.split("\\|");
        for(String pos:positions){
            if(pos.contains("&remain")){
                pos = pos.split("&")[0];
            }
            if("this".equals(pos)){ // this
                retVar = baseVar;
            }else if(pos.startsWith("param-")){ // param-0
                int index = Integer.valueOf(pos.split("-")[1]);
                retVar = args.get(index);

            }else if(retVar != null && StringUtils.isNumeric(pos)){
                int index = Integer.valueOf(pos);
                TabbyVariable tempVar = retVar.getElement(index);
                if(created && tempVar == null){
                    tempVar = TabbyVariable.makeRandomInstance();
                    boolean isPolluted = retVar.isPolluted(-1);
                    tempVar.getValue().setPolluted(isPolluted);
                    if(isPolluted){
                        tempVar.getValue().setRelatedType(retVar.getValue().getRelatedType()+"|"+index);
                    }
                    retVar.addElement(index, tempVar);
                }
                retVar = tempVar;
            }else if(retVar != null){
                TabbyVariable tempVar = retVar.getField(pos);
                if(created && tempVar == null){
                    SootField field = retVar.getSootField(pos);
                    if(field != null){
                        tempVar = retVar.getOrAddField(retVar, field);
                    }
                }
                retVar = tempVar;
            }else{
                retVar = null;
            }
        }
        return retVar;
    }
}
