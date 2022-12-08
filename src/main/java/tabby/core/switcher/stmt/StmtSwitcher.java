package tabby.core.switcher.stmt;

import lombok.Getter;
import lombok.Setter;
import soot.jimple.AbstractStmtSwitch;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.core.data.Context;
import tabby.core.container.DataContainer;
import tabby.core.switcher.value.ValueSwitcher;


@Getter
@Setter
public abstract class StmtSwitcher extends AbstractStmtSwitch {

    public Context context;
    public DataContainer dataContainer;
    public MethodReference methodRef;
    public ValueSwitcher leftValueSwitcher;
    public ValueSwitcher rightValueSwitcher;
    public boolean reset = true;
}
