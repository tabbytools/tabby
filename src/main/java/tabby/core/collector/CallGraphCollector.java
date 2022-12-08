package tabby.core.collector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soot.Modifier;
import soot.SootMethod;
import tabby.core.container.DataContainer;
import tabby.core.data.Context;
import tabby.core.switcher.Switcher;
import tabby.core.toolkit.PollutedVarsPointsToAnalysis;
import tabby.dal.caching.bean.ref.MethodReference;


@Slf4j
@Service
@Setter
public class CallGraphCollector {

//    @Async("multiCallGraphCollector")
    public void collect(MethodReference methodRef, DataContainer dataContainer){
        try{
            SootMethod method = methodRef.getMethod();
            if(method == null) return;

            if(method.isPhantom() || methodRef.isSink()
                    || methodRef.isIgnore() || method.isAbstract()
                    || Modifier.isNative(method.getModifiers())){
                methodRef.setInitialed(true);
                return;
            }

            if(method.isStatic() && method.getParameterCount() == 0){

                methodRef.setInitialed(true);
                return;
            }

            log.debug(method.getDeclaringClass().getName()+" "+method.getName());

            Context context = Context.newInstance(method.getSignature(), methodRef);

            PollutedVarsPointsToAnalysis pta =
                    Switcher.doMethodAnalysis(
                            context, dataContainer,
                            method, methodRef);
            context.clear();

        }catch (RuntimeException e){
            e.printStackTrace();
        }
    }

}
