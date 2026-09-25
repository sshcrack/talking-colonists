package me.sshcrack.mc_talking.client;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.guide.AddonGuide;
import me.sshcrack.mc_talking.internal.api.GuideServiceBackend;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Colony Handbook window, in MineColonies' style: the chapters on the left (Talking Colonists first,
 * then one per addon), the selected chapter on the right: what it is, how to start, and good to know.
 */
public final class HandbookWindow extends BOWindow {
    private static final int SELECTED_COLOR = 0x6B3A12;
    private static final int CHAPTER_COLOR = 0x000000;

    private final List<AddonGuide> guides = GuideServiceBackend.registered();
    private final ScrollingList chapters;
    private final ScrollingList paragraphs;
    private List<Component> shown = List.of();
    private int selected;

    private HandbookWindow() {
        super(layout());
        this.chapters = findPaneOfTypeByID("chapters", ScrollingList.class);
        this.paragraphs = findPaneOfTypeByID("paragraphs", ScrollingList.class);
        findPaneOfTypeByID("close", Button.class).setHandler(button -> close());
        chapters.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return guides.size();
            }

            @Override
            public void updateElement(int index, Pane row) {
                Button button = row.findPaneOfTypeByID("chapter", Button.class);
                button.setText(Component.literal(guides.get(index).title()));
                button.setTextColor(index == selected ? SELECTED_COLOR : CHAPTER_COLOR);
                button.setHandler(clicked -> select(index));
            }
        });
        paragraphs.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return shown.size();
            }

            @Override
            public void updateElement(int index, Pane row) {
                row.findPaneOfTypeByID("paragraph", Text.class).setText(shown.get(index));
            }
        });
        select(0);
    }

    /** Opens the handbook. Client side only. */
    public static void openHandbook() {
        new HandbookWindow().open();
    }

    private static ResourceLocation layout() {
        /*? if neoforge {*/
        return ResourceLocation.fromNamespaceAndPath(McTalking.MODID, "gui/handbook.xml");
        /*?}*/
        /*? if forge {*/
        /*return new ResourceLocation(McTalking.MODID, "gui/handbook.xml");
        *//*?}*/
    }

    private void select(int index) {
        if (index < 0 || index >= guides.size()) return;
        selected = index;
        AddonGuide guide = guides.get(index);
        findPaneOfTypeByID("chapterTitle", Text.class).setText(Component.literal(guide.title()));
        shown = paragraphs(guide);
        chapters.refreshElementPanes();
        paragraphs.refreshElementPanes();
        paragraphs.setScrollY(0);
    }

    /** The chapter as paragraphs: the summary, the numbered steps, then the notes. */
    static List<Component> paragraphs(AddonGuide guide) {
        List<Component> result = new ArrayList<>();
        result.add(Component.literal(guide.summary()));
        if (!guide.steps().isEmpty()) {
            result.add(Component.translatable("mc_talking.handbook.how_to_start").withStyle(ChatFormatting.BOLD));
            for (int i = 0; i < guide.steps().size(); i++) {
                result.add(Component.literal((i + 1) + ". " + guide.steps().get(i)));
            }
        }
        if (!guide.notes().isEmpty()) {
            result.add(Component.translatable("mc_talking.handbook.good_to_know").withStyle(ChatFormatting.BOLD));
            for (String note : guide.notes()) {
                result.add(Component.literal("• " + note));
            }
        }
        return result;
    }
}
