package me.sshcrack.mc_talking.manager.tools;

import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AITools {
    private AITools() {
        /* This utility class should not be instantiated */
    }

    private static final Map<String, FunctionAction> registeredFunctions = new HashMap<>();
    private static final Map<String, FunctionAction> playerConversationOnlyTools = new HashMap<>();

    private static void addAll(Map<String, FunctionAction> map, List<FunctionAction> actions) {
        for (var action : actions) {
            map.put(action.getName(), action);
        }
    }

    public static FunctionAction getAction(String name) {
        var action = registeredFunctions.get(name);
        if (action != null) return action;
        return playerConversationOnlyTools.get(name);
    }

    public static boolean isPlayerOnlyAction(String name) {
        var action = getAction(name);
        return action != null && action.isPlayerOnly();
    }

    public static List<String> getRegisteredFunctionNames() {
        var names = new ArrayList<>(registeredFunctions.keySet());
        names.addAll(playerConversationOnlyTools.keySet());
        return names;
    }

    public static List<BidiGenerateContentSetup.Tool> getEnabledTools() {
        var list = new ArrayList<BidiGenerateContentSetup.Tool>();

        var tool = new BidiGenerateContentSetup.Tool();
        var rawToolsDisabled = McTalkingConfig.INSTANCE.instance().disabledTools;

        var functions = Stream.concat(
                registeredFunctions.values().stream(),
                playerConversationOnlyTools.values().stream()
        );

        tool.functionDeclarations.addAll(
                functions
                        .filter(e -> !rawToolsDisabled.contains(e.getName()))
                        .filter(FunctionAction::isEnabled)
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
        addAll(registeredFunctions, List.of(
                new GetCitizenInfoAction(),
                new ListCitizenAction(),
                new GetInventoryAction(),
                new GetColonyAction(),
                new DescribeSurroundingsAction(),
                new DescribeBuildingAction(),
                new EndConversationAction(),
                new RecordRelationshipChange(),
                new AddEventToMemory(),
                new RecommendJobAction(),
                new GetCurrentSituationAction()
//                new JobSpecificAction()
        ));
        addAll(playerConversationOnlyTools, List.of(
                new DropItemAction(),
                new LeaveColonyAction(),
                new InitiateBroadcastAction()
        ));
    }
}
