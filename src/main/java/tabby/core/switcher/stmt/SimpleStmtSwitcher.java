package tabby.core.switcher.stmt;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.Local;
import soot.PrimType;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import tabby.config.GlobalConfiguration;
import tabby.core.data.TabbyVariable;
import tabby.core.switcher.Switcher;

import java.util.ArrayList;

@Getter
@Setter
@Slf4j
public class SimpleStmtSwitcher extends StmtSwitcher {

    @Override
    public void caseInvokeStmt(InvokeStmt stmt) {
        // extract baseVar and args
        InvokeExpr ie = stmt.getInvokeExpr();
        if("<java.lang.Object: void <init>()>".equals(ie.getMethodRef().getSignature())) return;
        if(GlobalConfiguration.DEBUG){
            log.debug("Analysis: "+ie.getMethodRef().getSignature() + "; "+context.getTopMethodSignature());
        }
        Switcher.doInvokeExprAnalysis(stmt, ie, dataContainer, context);
        if(GlobalConfiguration.DEBUG) {
            log.debug("Analysis: " + ie.getMethodRef().getName() + " done, return to" + context.getMethodSignature() + "; "+context.getTopMethodSignature());
        }
    }

    @Override
    public void caseAssignStmt(AssignStmt stmt) {
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        TabbyVariable rvar = null;
        boolean unbind = false;
        rightValueSwitcher.setUnit(stmt);
        rightValueSwitcher.setContext(context);
        rightValueSwitcher.setDataContainer(dataContainer);
        rightValueSwitcher.setResult(null);
        rop.apply(rightValueSwitcher);
        Object result = rightValueSwitcher.getResult();
        if(result instanceof TabbyVariable){
            rvar = (TabbyVariable) result;
        }
        if(rop instanceof Constant && !(rop instanceof StringConstant)){
            unbind = true;
        }
        if(rvar != null && rvar.getValue() != null && rvar.getValue().getType() instanceof PrimType){
            rvar = null;
        }
        // 处理左值
        if(rvar != null || unbind){
            leftValueSwitcher.setContext(context);
            leftValueSwitcher.setMethodRef(methodRef);
            leftValueSwitcher.setRvar(rvar);
            leftValueSwitcher.setUnbind(unbind);
            lop.apply(leftValueSwitcher);
        }
    }

    @Override
    public void caseIdentityStmt(IdentityStmt stmt) {
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        if(rop instanceof ThisRef){
            context.bindThis(lop);
        }else if(rop instanceof ParameterRef){
            ParameterRef pr = (ParameterRef)rop;
            context.bindArg((Local)lop, pr.getIndex());
        }
    }



    @Override
    public void caseReturnStmt(ReturnStmt stmt) {
        Value value = stmt.getOp();
        TabbyVariable var = null;

        if(context.getReturnVar() != null && context.getReturnVar().containsPollutedVar(new ArrayList<>())) return;
        rightValueSwitcher.setUnit(stmt);
        rightValueSwitcher.setContext(context);
        rightValueSwitcher.setDataContainer(dataContainer);
        rightValueSwitcher.setResult(null);
        value.apply(rightValueSwitcher);
        var = (TabbyVariable) rightValueSwitcher.getResult();
        context.setReturnVar(var);
        if(var != null && var.isPolluted(-1) && reset){
            methodRef.addAction("return", var.getValue().getRelatedType());
        }
    }

}
