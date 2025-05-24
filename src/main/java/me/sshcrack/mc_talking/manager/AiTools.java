package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup.Tool.FunctionDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class AiTools {
    public static final HashMap<String, FunctionAction> registeredFunctions = new HashMap<>();

    public record FunctionAction(FunctionDeclaration tool, Consumer<AbstractEntityCitizen> action) {}
    public static FunctionAction LEAVE_ACTION = new FunctionAction(
            new FunctionDeclaration("leave_colony", "You leave the colony. Only leave the colony when you are REALLY upset and sure you NEVER want to come back."),
            citizen -> {
                var colony = citizen.getCitizenColonyHandler().getColony();
                var manager = colony.getCitizenManager();
                var visitor = colony.getVisitorManager();

                var data = citizen.getCitizenData();
                var level = citizen.level();
                var pos = citizen.blockPosition();

                manager.unregisterCivilian(citizen);
                visitor.spawnOrCreateCivilian(data, level, pos, true);
            }
    );

    public static List<BidiGenerateContentSetup.Tool> getAllTools() {
        var list = new ArrayList<BidiGenerateContentSetup.Tool>();

        var tool = new BidiGenerateContentSetup.Tool();
        tool.functionDeclarations.addAll(
                registeredFunctions
                        .values()
                        .stream()
                        .map(FunctionAction::tool)
                        .toList()
        );

        list.add(tool);
        return list;
    }

    public static void add(FunctionAction action) {
        registeredFunctions.put(action.tool().name, action);
    }

    public static void register() {
        add(LEAVE_ACTION);
    }
}
