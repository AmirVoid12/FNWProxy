package iran.flame.network.proxy.kernel.alias;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import java.util.Arrays;

public record AliasContext(CommandSource source, String[] args, String label) {
    public int argsLength() {
        return args.length;
    }

    public boolean hasArg(int index) {
        return index >= 0 && index < args.length;
    }

    public String getArg(int index) {
        return hasArg(index) ? args[index] : null;
    }

    public String getArg(int index, String def) {
        String val = getArg(index);
        return val != null ? val : def;
    }

    public int getArgAsInt(int index, int def) {
        String val = getArg(index);
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String join(int fromIndex) {
        if (fromIndex >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length));
    }

    public AliasContext shift() {
        if (args.length == 0) {
            return new AliasContext(source, args, label);
        }
        return new AliasContext(source, Arrays.copyOfRange(args, 1, args.length), label);
    }

    public void reply(Component component) {
        source.sendMessage(component);
    }

    public boolean hasPermission(String permission) {
        return permission == null || permission.isEmpty() || source.hasPermission(permission);
    }
}