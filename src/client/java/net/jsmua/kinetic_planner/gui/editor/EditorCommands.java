package net.jsmua.kinetic_planner.gui.editor;

import com.mojang.brigadier.context.CommandContext;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import xaero.map.gui.GuiMap;

/**
 * 编辑模式命令处理器 - 从 {@code /kp edit} 和 {@code /kp exit} 分离。
 *
 * <p>这两个命令引用 GUI 类（{@link Minecraft}, {@link Screen}, {@link GuiMap},
 * {@link KpEditorScreen}），与其余 25 个非 GUI 命令分离。
 * 由 {@code KPClientCommands} 委托调用。
 */
public final class EditorCommands {

    private EditorCommands() {}

    /**
     * {@code /kp edit}：从观看模式进入编辑模式。
     *
     * <p>前置条件：当前 Screen 必须是 Xaero {@link GuiMap}。
     */
    public static int editMode(CommandContext<CommandSourceStack> ctx) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GuiMap guiMap)) {
            ctx.getSource().sendFailure(Component.literal(
                "[KP] Edit mode requires Xaero's World Map to be open"));
            return 0;
        }
        KpEditorScreen editorScreen = KpEditorScreen.create(guiMap);
        Minecraft.getInstance().setScreen(editorScreen);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Entered edit mode"), false);
        return 1;
    }

    /**
     * {@code /kp exit}：退出编辑模式，回到观看模式。
     */
    public static int exitEditMode(CommandContext<CommandSourceStack> ctx) {
        if (!KpClientState.isEditMode()) {
            ctx.getSource().sendFailure(Component.literal("[KP] Not in edit mode"));
            return 0;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof KpEditorScreen editorScreen) {
            editorScreen.onClose();
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Exited edit mode"), false);
        } else {
            KpClientState.setEditMode(false);
            ctx.getSource().sendSuccess(() ->
                Component.literal("[KP] Exited edit mode (forced)"), false);
        }
        return 1;
    }
}
