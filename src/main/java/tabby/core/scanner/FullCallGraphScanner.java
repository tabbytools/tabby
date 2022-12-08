package tabby.core.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Modifier;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import tabby.core.model.DefaultInvokeModel;
import tabby.dal.caching.bean.ref.MethodReference;

import java.util.ArrayList;
import java.util.Collection;


@Slf4j
@Component
public class FullCallGraphScanner extends CallGraphScanner{

    @Override
    public void collect() {
        Collection<MethodReference> targets =
                new ArrayList<>(dataContainer.getSavedMethodRefs().values());
//        log.info("Load necessary method refs.");
//        dataContainer.loadNecessaryMethodRefs();
        log.info("Build call graph. START!");
        total = targets.size();
        split = total / 10;
        split = split==0?1:split;
        int count = 0;
        for (MethodReference target : targets) {
            if(count%split == 0){
                log.info("Status: {}%, Remain: {}", String.format("%.1f",count*0.1/total*1000), (total-count));
            }
            buildCallEdge(target);
            count++;
        }
        log.info("Status: 100%, Remain: 0");
        log.info("Build call graph. DONE!");
    }

    public void buildCallEdge(MethodReference methodRef){
        try{
            SootMethod method = methodRef.getMethod();
            if(method == null) {
                return;
            }

            if(methodRef.isIgnore() || methodRef.isSink()){
                return;
            }

            if(method.isStatic() && method.getParameterCount() == 0){

                methodRef.setInitialed(true);
                return;
            }

            if(method.isAbstract()
                    || Modifier.isNative(method.getModifiers())
                    || method.isPhantom()){
                methodRef.setInitialed(true);
                methodRef.setActionInitialed(true);
                return;
            }

            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            DefaultInvokeModel model = new DefaultInvokeModel();
            for(Unit unit:body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if(stmt.containsInvokeExpr()){
                    InvokeExpr ie = stmt.getInvokeExpr();
                    SootMethod targetMethod = ie.getMethod();
                    MethodReference targetMethodRef
                            = dataContainer.getOrAddMethodRef(ie.getMethodRef(), targetMethod);
                    model.apply(stmt, false, methodRef, targetMethodRef, dataContainer);
                }
            }
        }catch (RuntimeException e){
//            log.error(e.getMessage());
            log.error("Something error on call graph. "+methodRef.getSignature());
            log.error(e.getMessage());
//            e.printStackTrace();
        }
    }
}
