package me.sshcrack.mc_talking.manager.tools;

import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AITools {
    private AITools() {
        /* This utility class should not be instantiated */
    }

    public static final Map<String, FunctionAction> registeredFunctions = new HashMap<>();

    public static void add(FunctionAction action) {
        registeredFunctions.put(action.getName(), action);
    }

    public static void addAll(List<FunctionAction> actions) {
        for (var action : actions) {
            add(action);
        }
    }

    public static List<String> getRegisteredFunctionNames() {
        return new ArrayList<>(registeredFunctions.keySet());
    }

    public static List<BidiGenerateContentSetup.Tool> getEnabledTools() {
        var list = new ArrayList<BidiGenerateContentSetup.Tool>();

        var tool = new BidiGenerateContentSetup.Tool();
        var rawToolsDisabled = McTalkingConfig.CONFIG.disabledTools.get();

        tool.functionDeclarations.addAll(
                registeredFunctions
                        .values()
                        .stream()
                        .filter(e -> !rawToolsDisabled.contains(e.getName()))
                        .map(e -> {
                            var declaration = new BidiGenerateContentSetup.Tool.FunctionDeclaration(e.getName(), e.getDescription());
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
                new GetInventoryAction(),
                new GetColonyAction(),
                new DropItemAction()
//                new JobSpecificAction()
        ));
    }
}
