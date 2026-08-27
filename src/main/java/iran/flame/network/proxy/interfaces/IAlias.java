package iran.flame.network.proxy.interfaces;

import iran.flame.network.proxy.kernel.alias.AliasContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface IAlias {
    void execute(AliasContext context);

    default List<String> tabComplete(AliasContext context) {
        return Collections.emptyList();
    }

    default Map<String, IAlias> getSubCommands() {
        return Collections.emptyMap();
    }
}