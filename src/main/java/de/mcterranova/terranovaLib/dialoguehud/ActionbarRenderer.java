package de.mcterranova.terranovaLib.dialoguehud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.betonquest.betonquest.api.common.component.ComponentLineWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns dialogue state into the lines of one actionbar message.
 * <p>
 * The actionbar is a single, horizontally centered line at a fixed height above the hotbar; the
 * client offers no way to place it elsewhere. With the row fonts and frames in the resource pack,
 * {@link GlyphIntegration} stacks several lines upwards from it; without them every page is one
 * line. All widths are measured with BetonQuest's line wrapper, which uses Minecraft's real glyph
 * widths (and the Nexo glyph widths registered by {@link GlyphIntegration}).
 */
final class ActionbarRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Component ELLIPSIS = Component.text("…");

    /** Answer counter for single-line pages, where only one answer is visible at a time. */
    private static final String COUNTER = " <dark_gray>(<index>/<count>)</dark_gray>";

    private final HudConfig config;

    private final ComponentLineWrapper wrapper;

    @Nullable
    private final GlyphIntegration glyphs;

    /** Lines per page. */
    private final int rows;

    ActionbarRenderer(final HudConfig config, final ComponentLineWrapper wrapper, @Nullable final GlyphIntegration glyphs) {
        this.config = config;
        this.wrapper = wrapper;
        this.glyphs = glyphs;
        this.rows = glyphs == null ? 1 : Math.min(config.maxLines(), glyphs.maxLines());
    }

    int width(final Component component) {
        return wrapper.width(component);
    }

    /**
     * Frame height in lines for a turn whose longest page has this many: never below
     * {@code hud.min-lines}, so a single short sentence still sits in a roomy frame, and never above
     * what the pack offers.
     */
    int frameRows(final int lines) {
        return Math.min(rows, Math.max(lines, config.minLines()));
    }

    /**
     * NPC icon, name and separator, shown at the start of every page.
     */
    Component prefix(final Component npcName, final String conversationId) {
        return parse(config.prefixFormat(),
                Placeholder.component("icon", icon(conversationId)),
                Placeholder.component("name", npcName));
    }

    /**
     * Splits NPC text into pages of up to {@link #rows} lines, at word boundaries. The first line of
     * every page starts with the prefix. Every line leaves room for the page arrow.
     */
    List<List<Component>> paginate(final Component prefix, final Component text) {
        if (Component.empty().equals(text)) {
            return List.of();
        }
        final int available = Math.max(40, config.maxTextWidth() - width(indicator(99, 99)));
        // The wrapper asks for a prefix once per line, in order.
        final int[] line = {0};
        final List<Component> lines = wrapper.splitWidth(text,
                () -> line[0]++ % rows == 0 ? prefix : Component.empty(), available);
        final List<List<Component>> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += rows) {
            pages.add(List.copyOf(lines.subList(start, Math.min(lines.size(), start + rows))));
        }
        return pages;
    }

    /**
     * A page of NPC text.
     *
     * @param showNext whether the arrow towards the next page (or the answers) ends the last line
     */
    List<Component> readingLines(final List<Component> lines, final boolean showNext, final int page, final int pages) {
        if (!showNext || lines.isEmpty()) {
            return lines;
        }
        final List<Component> shown = new ArrayList<>(lines);
        shown.set(shown.size() - 1, shown.getLast().append(indicator(page, pages)));
        return shown;
    }

    /**
     * The answers. With one line per page, only the selected one with a counter; otherwise a header
     * line and a list that scrolls so the selected answer stays visible. Answers that do not fit are
     * shortened with an ellipsis.
     */
    List<Component> optionLines(final Component prefix, final List<Component> options, final int selected) {
        final int count = options.size();
        if (rows == 1) {
            return List.of(prefix.append(option(config.optionFormat(), options.get(selected), selected + 1, count,
                    width(prefix) + width(counter(selected + 1, count)))).append(counter(selected + 1, count)));
        }
        final List<Component> lines = new ArrayList<>();
        lines.add(prefix.append(parse(config.chooseFormat(),
                Placeholder.unparsed("index", String.valueOf(selected + 1)),
                Placeholder.unparsed("count", String.valueOf(count)))));
        final int visible = Math.min(count, rows - 1);
        final int first = Math.min(Math.max(0, selected - visible + 1), count - visible);
        for (int i = first; i < first + visible; i++) {
            lines.add(option(i == selected ? config.optionFormat() : config.optionIdleFormat(), options.get(i),
                    i + 1, count, 0));
        }
        return lines;
    }

    /**
     * Wraps lines for sending: inside the frame when glyphs are available, otherwise the single line
     * padded with trailing spaces so a typed line does not drift while the centered actionbar grows.
     *
     * @param frameRows  frame height in lines, so all pages of an NPC turn share one frame
     * @param cycleWidth the widest line of the current NPC turn, so all its pages share one frame width
     */
    Component finish(final List<Component> lines, final int frameRows, final int cycleWidth) {
        final List<Integer> widths = lines.stream().map(this::width).toList();
        if (glyphs != null) {
            final int padding = padding(cycleWidth, config.padding(), config.paddingRatio());
            return glyphs.frame(lines, widths, frameRows, cycleWidth + 2 * padding, padding);
        }
        // A space is 4 px wide in the vanilla font.
        final int spaces = Math.max(0, (cycleWidth - widths.getFirst()) / 4);
        return spaces == 0 ? lines.getFirst() : lines.getFirst().append(Component.text(" ".repeat(spaces)));
    }

    /**
     * Space between the frame's inner edge and the text: never below the minimum, and growing with
     * the line, so a long line does not look crammed into its frame.
     */
    static int padding(final int textWidth, final int minimum, final double ratio) {
        return Math.max(minimum, (int) Math.round(textWidth * ratio));
    }

    private Component option(final String format, final Component option, final int index, final int count,
                             final int besides) {
        final int available = config.maxTextWidth() - besides
                - width(optionBody(format, Component.empty(), index, count));
        Component fitted = option;
        if (width(option) > available) {
            final List<Component> parts = wrapper.splitWidth(option, Math.max(10, available - width(ELLIPSIS)));
            fitted = parts.getFirst().append(ELLIPSIS);
        }
        return optionBody(format, fitted, index, count);
    }

    private Component optionBody(final String format, final Component option, final int index, final int count) {
        return parse(format,
                Placeholder.component("selector", selector()),
                Placeholder.component("option", option),
                Placeholder.unparsed("index", String.valueOf(index)),
                Placeholder.unparsed("count", String.valueOf(count)));
    }

    private Component counter(final int index, final int count) {
        return parse(COUNTER,
                Placeholder.unparsed("index", String.valueOf(index)),
                Placeholder.unparsed("count", String.valueOf(count)));
    }

    private Component indicator(final int page, final int pages) {
        final Component arrow = parse(config.nextFormat());
        if (pages <= 1) {
            return arrow;
        }
        return arrow.append(parse(config.progressFormat(),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages))));
    }

    private Component icon(final String conversationId) {
        final String id = config.npcIcons().getOrDefault(conversationId, config.npcIcons().getOrDefault("default", ""));
        final GlyphIntegration.Piece piece = glyphs == null || id.isEmpty() ? null : glyphs.extra(id);
        return piece == null ? Component.empty() : piece.component();
    }

    private Component selector() {
        final GlyphIntegration.Piece piece = glyphs == null || config.selectorGlyph().isEmpty()
                ? null : glyphs.extra(config.selectorGlyph());
        return piece == null ? parse(config.selectorFormat()) : piece.component();
    }

    private static Component parse(final String format, final TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(format, resolvers);
    }

    /**
     * Counts the characters a typing effect can reveal.
     */
    static int length(final Component component) {
        int length = component instanceof final TextComponent text ? text.content().codePointCount(0, text.content().length()) : 0;
        for (final Component child : component.children()) {
            length += length(child);
        }
        return length;
    }

    static int length(final List<Component> lines) {
        return lines.stream().mapToInt(ActionbarRenderer::length).sum();
    }

    /**
     * The first {@code count} characters of a page, line by line; lines not reached yet stay empty,
     * so they keep their place.
     */
    static List<Component> reveal(final List<Component> lines, final int count) {
        final List<Component> shown = new ArrayList<>(lines.size());
        int budget = count;
        for (final Component line : lines) {
            shown.add(budget <= 0 ? Component.empty() : truncate(line, budget));
            budget -= length(line);
        }
        return shown;
    }

    /**
     * The first {@code count} characters of a component, keeping their styles.
     */
    static Component truncate(final Component component, final int count) {
        return truncate(component, new int[]{count});
    }

    private static Component truncate(final Component component, final int[] budget) {
        Component result = component.children(List.of());
        if (component instanceof final TextComponent text) {
            final String content = text.content();
            final int length = content.codePointCount(0, content.length());
            if (length > budget[0]) {
                result = text.children(List.of()).content(content.substring(0, content.offsetByCodePoints(0, budget[0])));
                budget[0] = 0;
                return result;
            }
            budget[0] -= length;
        }
        for (final Component child : component.children()) {
            if (budget[0] <= 0) {
                break;
            }
            result = result.append(truncate(child, budget));
        }
        return result;
    }
}
