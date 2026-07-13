package xyz.geik.farmer.modules.spawnerkiller.update;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test
    void permitsOnlyOperatorsAndFarmerAdmins() {
        assertTrue(UpdateChecker.canNotify(player(true, false)));
        assertTrue(UpdateChecker.canNotify(player(false, true)));
        assertFalse(UpdateChecker.canNotify(player(false, false)));
    }

    @Test
    void fillsEveryMessagePlaceholder() {
        assertEquals("SpawnerKiller|1.1.0|v1.1.1|https://github.com/release",
                UpdateChecker.formatMessage("{module}|{current}|{latest}|{url}",
                        "SpawnerKiller", "1.1.0", "v1.1.1", "https://github.com/release"));
    }

    private static Player player(boolean operator, boolean admin) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isOp" -> operator;
                    case "hasPermission" -> admin;
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }
}
