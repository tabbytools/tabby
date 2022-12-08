package tabby.core.data;

import lombok.Data;
import soot.Local;
import soot.SootField;
import soot.SootFieldRef;
import soot.Value;
import soot.jimple.InstanceFieldRef;
import soot.jimple.StaticFieldRef;
import tabby.dal.caching.bean.ref.MethodReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


@Data
public class Context {

    private String methodSignature;
    private MethodReference methodReference;
    private Map<Local, TabbyVariable> initialMap;
    private Local thisVar;
    private Map<Integer, Local> args = new HashMap<>();
    private Context preContext;
    private int depth;

    private Map<Local, TabbyVariable> localMap;
    private Map<Local, Set<TabbyVariable>> maybeLocalMap = new HashMap<>();
    private Map<Value, TabbyVariable> globalMap = new HashMap<>();

    private TabbyVariable returnVar;
    private String topMethodSignature;

    public Context(){
        this.localMap = new HashMap<>();
    }

    public Context(String methodSignature, MethodReference methodReference, Context preContext, int depth) {
        this.methodSignature = methodSignature;
        this.methodReference = methodReference;
        this.topMethodSignature = methodSignature;
        this.depth = depth;
        this.preContext = preContext;
        this.localMap = new HashMap<>();
    }

    public static Context newInstance(String methodSignature, MethodReference methodReference) {
        return new Context(methodSignature, methodReference,null,0);
    }


    public Context createSubContext(String methodSignature, MethodReference methodReference) {
        Context subContext = new Context(methodSignature, methodReference, this,depth + 1);
        subContext.setGlobalMap(globalMap);
        subContext.setTopMethodSignature(topMethodSignature);
        return subContext;
    }



    public TabbyVariable getOrAdd(Value sootValue) {
        TabbyVariable var = null;
        if(sootValue instanceof Local){ // find from local map
            var = localMap.get(sootValue);
            if(var == null){
                TabbyVariable tempVar = initialMap.get(sootValue);
                if(tempVar != null){
                    var = tempVar.deepClone(new ArrayList<>());
                    localMap.put((Local) sootValue, var);
                }
            }
            if (var == null) {
                var = TabbyVariable.makeLocalInstance((Local)sootValue);
                localMap.put((Local) sootValue, var);
            }
        }else if(sootValue instanceof StaticFieldRef){ // find from global map
            var = globalMap.get(sootValue);
            if(var == null){
                var = TabbyVariable.makeStaticFieldInstance((StaticFieldRef) sootValue);
                globalMap.put(sootValue, var);
            }
        }else if(sootValue instanceof InstanceFieldRef){
            InstanceFieldRef ifr = (InstanceFieldRef) sootValue;
            SootField sootField = ifr.getField();
            SootFieldRef fieldRef = ifr.getFieldRef();

            String signature = null;
            if(sootField != null){
                signature = sootField.getSignature();
            }else if(fieldRef != null){
                signature = fieldRef.getSignature();
            }

            Value base = ifr.getBase();
            if(base instanceof Local){
                TabbyVariable baseVar = getOrAdd(base);
                var = baseVar.getField(signature);
                if(var == null){
                    if(sootField != null){
                        var = baseVar.getOrAddField(baseVar, sootField);
                    }else if(fieldRef != null){
                        var = baseVar.getOrAddField(baseVar, fieldRef);
                    }
                }
                if(var != null){
                    var.setOrigin(ifr);
                }
            }
        }
        return var;
    }

    public void bindThis(Value value) {
        if(value instanceof Local){
            thisVar = (Local) value;
            TabbyVariable var = getOrAdd(thisVar);
            var.setThis(true);
            var.getValue().setPolluted(true);
            var.getValue().setRelatedType("this");
            var.getFieldMap().forEach((fieldName, fieldVar) -> {
                if(fieldVar != null){
                    fieldVar.getValue().setPolluted(true);
                    fieldVar.getValue().setRelatedType("this|"+fieldName);
                }
            });
        }
    }

    public void bindArg(Local local, int paramIndex) {
        TabbyVariable paramVar = getOrAdd(local);
        paramVar.setParam(true);
        paramVar.setParamIndex(paramIndex);
        paramVar.getValue().setPolluted(true);
        paramVar.getValue().setRelatedType("param-"+paramIndex);

        paramVar.getFieldMap().forEach((fieldName, fieldVar) -> {
            if(fieldVar != null){
                fieldVar.getValue().setPolluted(true);
                fieldVar.getValue().setRelatedType("param-"+paramIndex+"|"+fieldName);
            }
        });
        args.put(paramIndex, local);
    }

    public void unbind(Value value){
        if(localMap.containsKey(value)){
            localMap.remove(value);
        }else if(globalMap.containsKey(value)){
            globalMap.remove(value);
        }
    }


    public boolean isInRecursion(String invokeSignature) {
        if (invokeSignature.equals(methodSignature)) {
            return true;
        }
        if (preContext != null) {
            return preContext.isInRecursion(invokeSignature);
        }
        return false;
    }

    public void clear(){
        globalMap.clear();
        maybeLocalMap.clear();
    }
}
