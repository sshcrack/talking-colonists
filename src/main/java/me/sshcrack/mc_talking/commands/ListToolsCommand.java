package me.sshcrack.mc_talking.commands;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.gemini_live_lib.gson.properties.PrimitiveProperty;
import me.sshcrack.mc_talking.manager.tools.AITools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.StringWriter;

public class ListToolsCommand {
    private static final SimpleCommandExceptionType NO_TOOLS = new SimpleCommandExceptionType(Component.translatable("mc_talking.commands.no_tools_available"));
    private static final SimpleCommandExceptionType TOOL_NOT_FOUND = new SimpleCommandExceptionType(Component.translatable("mc_talking.commands.tool_not_found"));

    private static int get_description(CommandContext<CommandSourceStack> context, String toolName) throws CommandSyntaxException {
        var src = context.getSource();

        var tool = AITools.registeredFunctions.get(toolName);
        if (tool == null) {
            throw TOOL_NOT_FOUND.create();
        }

        var strWriter = new StringWriter();
        var prop = tool.getProperty();
        if (prop instanceof ObjectProperty objProp) {
            objProp.getProperties().forEach((key, value) -> {
                strWriter.write("\n");
                strWriter.write(key + ": ");
                if (value instanceof PrimitiveProperty prim) {
                    strWriter.write(prim.getType());
                } else if (value instanceof EnumProperty enumP) {
                    var gson = new Gson();
                    var s = new StringWriter();
                    gson.toJson(enumP, s);
                    var elem = JsonParser.parseString(s.toString());

                    var arr = elem.getAsJsonObject().get("enum").getAsJsonArray();
                    strWriter.write(arr.toString());
                } else {
                    strWriter.write("Unsupported type");
                }
                //TODO Add array
            });
        }

        src.sendSuccess(() -> Component.translatable("mc_talking.commands.tool_description", toolName, tool.getDescription(), strWriter.toString()), true);
        return 0;
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        var builder = Commands.literal("list_tools");

        for (String fn_name : AITools.getRegisteredFunctionNames()) {
            builder.then(Commands.literal(fn_name)
                    .executes(context -> get_description(context, fn_name)));
        }

        root.then(builder
                .executes(ctx -> {
                    var src = ctx.getSource();
                    var tools = AITools.getRegisteredFunctionNames();

                    if (tools.isEmpty()) {
                        throw NO_TOOLS.create();
                    }

                    src.sendSuccess(() -> Component.translatable("mc_talking.commands.list_tools", String.join(", ", tools)), true);
                    return 1;
                }));
    }
}
