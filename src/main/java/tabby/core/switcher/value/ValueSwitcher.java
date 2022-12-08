package tabby.core.switcher.value;

import lombok.Getter;
import lombok.Setter;
import soot.Unit;
import soot.jimple.AbstractJimpleValueSwitch;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.core.data.Context;
import tabby.core.container.DataContainer;
import tabby.core.data.TabbyVariable;

@Getter
@Setter
public abstract class ValueSwitcher extends AbstractJimpleValueSwitch {

    public Context context;
    public DataContainer dataContainer;
    public MethodReference methodRef;
    public TabbyVariable rvar;
    public boolean unbind = false;
    public boolean reset = true;
    public Unit unit;

}
