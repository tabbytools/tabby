package tabby.core.switcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;
import tabby.core.data.TabbyVariable;
import tabby.core.toolkit.PollutedVarsPointsToAnalysis;
import tabby.dal.caching.bean.edge.Call;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.util.PositionHelper;

import java.util.*;


@Setter
@Getter
@Slf4j
public class InvokeExprSwitcher extends AbstractJimpleValueSwitch {

    private MethodReference source;
    private Unit unit;
    private PollutedVarsPointsToAnalysis pta;

    private Value baseValue;
    private boolean isPolluted = false;
    private List<Integer> pollutedPosition;
    private Map<Value, TabbyVariable> globalMap = new HashMap<>();
    private Map<Local, TabbyVariable> localMap = new HashMap<>();

    private DataContainer dataContainer;
    private RulesContainer rulesContainer;


    @Override
    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
        if(isNecessaryEdge("StaticInvoke", v)){
            SootMethodRef sootMethodRef = v.getMethodRef();
            generate(v);
            buildCallRelationship(sootMethodRef.getDeclaringClass().getName(), sootMethodRef, "StaticInvoke");
        }
    }

    @Override
    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) { // a.A()
        SootMethodRef sootMethodRef = v.getMethodRef();
        baseValue = v.getBase();
        generate(v);
        buildCallRelationship(v.getBase().getType().toString(), sootMethodRef, "VirtualInvoke");
    }

    @Override
    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
        SootMethodRef sootMethodRef = v.getMethodRef();
        if(sootMethodRef.getSignature().contains("<init>") && v.getArgCount() == 0) return;
        baseValue = v.getBase();
        generate(v);
        buildCallRelationship(v.getBase().getType().toString(), sootMethodRef, "SpecialInvoke");
    }

    @Override
    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        SootMethodRef sootMethodRef = v.getMethodRef();
        baseValue = v.getBase();
        generate(v);
        buildCallRelationship(v.getBase().getType().toString(), sootMethodRef, "InterfaceInvoke");
    }

    public void buildCallRelationship(String classname, SootMethodRef sootMethodRef, String invokerType){
        MethodReference target = dataContainer.getOrAddMethodRef(sootMethodRef, sootMethodRef.resolve());
        MethodReference source = dataContainer.getMethodRefBySignature(this.source.getClassname(), this.source.getSignature());

        if(target.isSink()){

            for(int i:target.getPollutedPosition()){
                if(pollutedPosition.size() > i+1 && pollutedPosition.get(i+1) == PositionHelper.NOT_POLLUTED_POSITION){
                    isPolluted = false;
                    break;
                }
            }
        }

        if(source != null
                && !target.isIgnore()
                && isPolluted){

            if("java.lang.String".equals(classname)
                    && ("equals".equals(target.getName())
                        || "hashCode".equals(target.getName())
                        || "length".equals(target.getName()))) return;

            if("java.lang.StringBuilder".equals(classname)
                    && ("toString".equals(target.getName())
                        || "hashCode".equals(target.getName()))) return;

            Call call = Call.newInstance(source, target);
            call.setRealCallType(classname);
            call.setInvokerType(invokerType);
            call.setPollutedPosition(new ArrayList<>(pollutedPosition));
            call.setUnit(unit);
            call.setLineNum(unit.getJavaSourceStartLineNumber());
            if(!source.getCallEdge().contains(call)){
                source.getCallEdge().add(call);
                dataContainer.store(call);
            }

        }
    }

    public <T> boolean isNecessaryEdge(String type, T v){
        if ("StaticInvoke".equals(type)) {
            StaticInvokeExpr invokeExpr = (StaticInvokeExpr) v;
            if (invokeExpr.getArgCount() == 0) {
                return false;
            }
            List<Value> values = invokeExpr.getArgs();
            for (Value value : values) {
                if (value instanceof JimpleLocal ||
                        ("forName".equals(invokeExpr.getMethodRef().getName()) && value instanceof StringConstant)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void generate(InvokeExpr ie){
        if(pta == null) return;
        pollutedPosition = new LinkedList<>();
        Map<Local, TabbyVariable> localMap = pta.getFlowBefore(unit);

        if(baseValue != null){
            // boolean int long double float short char
            if(baseValue.getType() instanceof PrimType){
                return;
            }else if(baseValue.getType() instanceof ArrayType){
                Type baseType = ((ArrayType) baseValue.getType()).baseType;
                if(baseType instanceof PrimType){
                    return;
                }
            }
        }
        pollutedPosition.add(check(baseValue, localMap));

        for(int i=0; i<ie.getArgCount(); i++){
            pollutedPosition.add(check(ie.getArg(i), localMap));
        }

        for(Integer i:pollutedPosition){
            if (i != PositionHelper.NOT_POLLUTED_POSITION) {
                isPolluted = true;
                break;
            }
        }
    }

    public int check(Value value, Map<Local, TabbyVariable> localMap){
        if(value == null){
            return PositionHelper.NOT_POLLUTED_POSITION;
        }
        TabbyVariable var = null;
        if(value instanceof Local){
            var = localMap.get(value);
        }else if(value instanceof StaticFieldRef){
            var = globalMap.get(value);
        }else if(value instanceof ArrayRef){
            ArrayRef ar = (ArrayRef) value;
            Value baseValue = ar.getBase();
            Value indexValue = ar.getIndex();
            if(baseValue instanceof Local){
                var = localMap.get(baseValue);
                if(indexValue instanceof IntConstant){
                    int index = ((IntConstant) indexValue).value;
                    var = var.getElement(index);
                }
            }
        }else if(value instanceof InstanceFieldRef){
            InstanceFieldRef ifr = (InstanceFieldRef) value;
            SootField sootField = ifr.getField();
            Value base = ifr.getBase();
            if(base instanceof Local){
                var = localMap.get(base);
                var = var.getField(sootField.getSignature());
            }
        }
        if(var != null){
            String related = null;
            if(var.isPolluted(PositionHelper.THIS)){
                related = var.getValue().getRelatedType();
            }else if(var.containsPollutedVar(new ArrayList<>())){
                related = var.getFirstPollutedVarRelatedType();
            }
            if(related != null){
                return PositionHelper.getPosition(related);
            }
        }
        return PositionHelper.NOT_POLLUTED_POSITION;
    }
}
