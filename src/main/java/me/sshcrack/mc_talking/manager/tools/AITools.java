package me.sshcrack.mc_talking.manager.tools;

import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup.Tool.FunctionDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AITools {
    public static final HashMap<String, FunctionAction> registeredFunctions = new HashMap<>();

    public static void add(FunctionAction action) {
        registeredFunctions.put(action.getName(), action);
    }

    public static void addAll(List<FunctionAction> actions) {
        for (var action : actions) {
            add(action);
        }
    }

    public static List<BidiGenerateContentSetup.Tool> getAllTools() {
        var list = new ArrayList<BidiGenerateContentSetup.Tool>();

        var tool = new BidiGenerateContentSetup.Tool();
        tool.functionDeclarations.addAll(
                registeredFunctions
                        .values()
                        .stream()
                        .map(e -> {
                            var declaration = new FunctionDeclaration(e.getName(), e.getDescription());
                            if (e.getProperty() != null)
                                declaration.parameters = e.getProperty();

                            return declaration;
                        })
                        .toList()
        );

        list.add(tool);
        return list;
    }

    public static void register() {
        addAll(List.of(
                new LeaveColonyAction(),
                new GetCitizenInfoAction(),
                new ListCitizenAction(),
                new GetInventoryAction()
        ));
    }
}
