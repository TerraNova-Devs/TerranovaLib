package de.mcterranova.terranovaLib.dialoguehud;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.betonquest.betonquest.api.common.component.ComponentLineWrapper;
import org.betonquest.betonquest.api.common.component.font.DefaultFontRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueHudTest {

    private static final int CAP = 6;

    @Test
    void packCountsAsMissingOnlyWithAKnownBadStatus() {
        // No status: a server switch behind NexoProxy, which does not resend the pack.
        assertFalse(ConversationIntegration.packMissing(null));
        assertFalse(ConversationIntegration.packMissing(Status.SUCCESSFULLY_LOADED));
        for (final Status status : Status.values()) {
            if (status != Status.SUCCESSFULLY_LOADED) {
                assertTrue(ConversationIntegration.packMissing(status), status.name());
            }
        }
    }

    @Test
    void typingRevealsWholeCharactersAndKeepsStyles() {
        final Component text = Component.text("Hallo ", NamedTextColor.GOLD)
                .append(Component.text("Wälder 🌲", NamedTextColor.GRAY));

        assertEquals(14, ActionbarRenderer.length(text));
        final Component partial = ActionbarRenderer.truncate(text, 8);
        assertEquals("Hallo Wä", plain(partial));
        assertEquals(NamedTextColor.GRAY, partial.children().getFirst().color());
        assertEquals("Hallo Wälder ", plain(ActionbarRenderer.truncate(text, 13)));
        assertEquals("Hallo Wälder 🌲", plain(ActionbarRenderer.truncate(text, 14)));
    }

    @Test
    void typingRunsAcrossLinesAndKeepsTheirPlaces() {
        final List<Component> page = List.of(Component.text("Eins "), Component.text("zwei "), Component.text("drei"));

        assertEquals(14, ActionbarRenderer.length(page));
        assertEquals(List.of("Eins ", "zw", ""), ActionbarRenderer.reveal(page, 7).stream().map(DialogueHudTest::plain).toList());
        assertEquals(List.of("Eins ", "zwei ", "drei"), ActionbarRenderer.reveal(page, 99).stream().map(DialogueHudTest::plain).toList());
    }

    @Test
    void paddingKeepsItsMinimumAndGrowsWithLongLines() {
        assertEquals(8, ActionbarRenderer.padding(40, 8, 0.05));
        assertEquals(8, ActionbarRenderer.padding(160, 8, 0.05));
        assertEquals(11, ActionbarRenderer.padding(220, 8, 0.05));
        assertEquals(15, ActionbarRenderer.padding(300, 8, 0.05));
    }

    @Test
    void glyphWidthIsRightmostOpaqueColumnScaledToGlyphHeight() {
        final BufferedImage image = new BufferedImage(8, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 3, 0xFF000000);

        assertEquals(6, GlyphIntegration.visibleWidth(image, 16));
        assertEquals(3, GlyphIntegration.visibleWidth(image, 8));
    }

    @Test
    void frameIsGaplessAndAdvancesExactlyItsWidth() {
        // Exact tiling, and a tile set that needs the backwards overlap.
        assertFrame(List.of(16, 4, 1), 50);
        assertFrame(List.of(16, 4), 50);
        assertFrame(List.of(128, 64, 32, 16, 8, 4, 2, 1), 229);
    }

    @Test
    void linesStartAtTheSameSpotInTheirOwnRows() {
        final GlyphIntegration glyphs = glyphs(3, List.of(128, 64, 32, 16, 8, 4, 2, 1));
        final List<Component> lines = List.of(Component.text("top"), Component.text("middle"), Component.text("bottom"));
        final Component framed = glyphs.frame(lines, List.of(10, 20, 5), 3, 50, 4);

        final List<Integer> starts = new ArrayList<>();
        final List<Key> fonts = new ArrayList<>();
        final int end = walk(framed, (content, font, cursor) -> {
            if (content.equals("top") || content.equals("middle") || content.equals("bottom")) {
                starts.add(cursor);
                fonts.add(font);
            }
        }, Map.of("top", 10, "middle", 20, "bottom", 5));

        assertEquals(List.of(CAP + 4, CAP + 4, CAP + 4), starts, "every line starts behind the padding");
        assertEquals(Key.key("test:row2"), fonts.get(0));
        assertEquals(Key.key("test:row1"), fonts.get(1));
        assertNull(fonts.get(2), "the bottom line stays in the actionbar's own font");
        assertEquals(CAP + 50 + CAP, end, "advance");
    }

    @Test
    void pagesHoldUpToTheLineLimitAndStartWithThePrefix() {
        final ActionbarRenderer renderer = renderer(3);
        final Component prefix = Component.text("Friedolin: ");
        final String text = String.join(" ", IntStream.range(0, 60).mapToObj(i -> "wort" + i).toList());

        final List<List<Component>> pages = renderer.paginate(prefix, Component.text(text));

        assertTrue(pages.size() > 1);
        for (final List<Component> page : pages) {
            assertTrue(page.size() <= 3);
            assertTrue(plain(page.getFirst()).startsWith("Friedolin: "), plain(page.getFirst()));
            page.subList(1, page.size()).forEach(line -> assertTrue(!plain(line).startsWith("Friedolin"), plain(line)));
            page.forEach(line -> assertTrue(renderer.width(line) <= 300, renderer.width(line) + " px"));
        }
        final String all = String.join("", pages.stream().flatMap(List::stream).map(DialogueHudTest::plain).toList())
                .replace("Friedolin: ", "");
        assertEquals(text.replace(" ", ""), all.replace(" ", ""), "no word lost or doubled");
    }

    @Test
    void frameIsAtLeastTwoLinesHighWherePossible() {
        assertEquals(2, renderer(3).frameRows(1), "a single sentence still gets two lines");
        assertEquals(3, renderer(3).frameRows(3));
        assertEquals(3, renderer(3).frameRows(5), "never more than the pack offers");
        assertEquals(1, renderer(1).frameRows(1), "without multi-line support it stays one line");
    }

    @Test
    void answersFormAScrollingListBelowAHeader() {
        final ActionbarRenderer renderer = renderer(4);
        final List<Component> options = IntStream.rangeClosed(1, 5).mapToObj(i -> (Component) Component.text("Antwort " + i)).toList();

        final List<Component> first = renderer.optionLines(Component.text("F: "), options, 0);
        final List<Component> last = renderer.optionLines(Component.text("F: "), options, 4);

        assertEquals(4, first.size(), "header plus three answers");
        assertTrue(plain(first.get(1)).contains("Antwort 1"));
        assertTrue(plain(last.getLast()).contains("Antwort 5"), "the selected answer scrolls into view");
        assertTrue(plain(last.get(1)).contains("Antwort 3"));
    }

    private interface Visitor {
        void text(String content, Key font, int cursor);
    }

    /**
     * Lays the component out the way the client does: every glyph advances by its width plus 1,
     * every shift by its value, every text by the given width.
     *
     * @return the final cursor position
     */
    private static int walk(final Component component, final Visitor visitor, final Map<String, Integer> textWidths) {
        int cursor = 0;
        for (final Component child : component.children()) {
            final Key font = child.font();
            final Component leaf = child instanceof final TextComponent text && text.content().isEmpty()
                    && child.children().size() == 1 ? child.children().getFirst() : child;
            final String content = ((TextComponent) leaf).content();
            if (content.startsWith("shift ")) {
                cursor += Integer.parseInt(content.substring(6));
            } else if (textWidths.containsKey(content)) {
                visitor.text(content, font, cursor);
                cursor += textWidths.get(content);
            } else {
                cursor += Integer.parseInt(content) + 1;
            }
        }
        return cursor;
    }

    /**
     * Lays the frame out the way the client does: every glyph advances by its width plus 1,
     * every shift by its value.
     */
    private static void assertFrame(final List<Integer> tileWidths, final int inner) {
        final int padding = 4;
        final int contentWidth = 30;
        final GlyphIntegration glyphs = glyphs(1, tileWidths);
        final Component framed = glyphs.frame(Component.text("content"), contentWidth, inner, padding);

        final int total = CAP + inner + CAP;
        final boolean[] drawn = new boolean[total];
        int cursor = 0;
        int contentStart = -1;
        for (final Component child : framed.children()) {
            final String content = ((TextComponent) child).content();
            if (content.startsWith("shift ")) {
                cursor += Integer.parseInt(content.substring(6));
            } else if ("content".equals(content)) {
                contentStart = cursor;
                cursor += contentWidth;
            } else {
                final int width = Integer.parseInt(content);
                for (int x = cursor; x < cursor + width; x++) {
                    assertTrue(x >= 0 && x < total, "glyph drawn outside the frame at " + x);
                    drawn[x] = true;
                }
                cursor += width + 1;
            }
        }
        assertEquals(total, cursor, "advance");
        assertEquals(CAP + padding, contentStart, "text start");
        for (int x = 0; x < total; x++) {
            assertTrue(drawn[x], "gap at " + x);
        }
    }

    /**
     * Glyphs whose components are their widths as text, shifts as "shift n", frames for 1..lines
     * lines and a row font per row.
     */
    private static GlyphIntegration glyphs(final int lines, final List<Integer> tileWidths) {
        final List<GlyphIntegration.FrameSet> frames = new ArrayList<>();
        final List<Key> rowFonts = new ArrayList<>();
        for (int n = 1; n <= lines; n++) {
            frames.add(new GlyphIntegration.FrameSet(piece(CAP), piece(CAP), tileWidths.stream().map(DialogueHudTest::piece).toList()));
            if (n < lines) {
                rowFonts.add(Key.key("test:row" + n));
            }
        }
        return new GlyphIntegration(frames, rowFonts, Map.of(), pixels -> Component.text("shift " + pixels));
    }

    /**
     * A renderer with the default config, a font where every character is 5 px wide, and frames for
     * this many lines.
     */
    private static ActionbarRenderer renderer(final int lines) {
        final DefaultFontRegistry fonts = new DefaultFontRegistry(Key.key("default"));
        fonts.registerFont(Key.key("default"), codepoint -> 5);
        return new ActionbarRenderer(HudConfig.from(new YamlConfiguration()), new ComponentLineWrapper(fonts),
                glyphs(lines, List.of(1)));
    }

    private static GlyphIntegration.Piece piece(final int width) {
        return new GlyphIntegration.Piece(Component.text(String.valueOf(width)), width);
    }

    private static String plain(final Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
