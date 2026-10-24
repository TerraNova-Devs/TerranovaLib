package de.mcterranova.terranovaLib.dialoguehud;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.conversation.Conversation;
import org.betonquest.betonquest.conversation.ConversationIO;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One player's conversation, shown in the actionbar.
 * <p>
 * BetonQuest stays in charge of everything a conversation means: it picks the NPC text and the
 * answers, checks conditions, runs actions and moves the quest on. This class only shows what it is
 * handed and reports back which answer was picked, by its number, through
 * {@link Conversation#passPlayerAnswer(int)}.
 * <p>
 * BetonQuest calls the {@link ConversationIO} methods from its own async threads and from the main
 * thread, input arrives on the player's thread and the HUD ticks on the player's entity scheduler,
 * so all state is guarded by this object's monitor. BetonQuest is never called while holding it.
 */
final class DialogueSession implements ConversationIO {

    private enum Mode {
        /** Nothing to show yet, or BetonQuest is working on the picked answer. */
        WAITING,
        /** Paging through NPC text. */
        READING,
        /** Picking an answer. */
        SELECTING,
        /** The conversation is over; its last NPC line stays until read. */
        EPILOGUE
    }

    private record Option(Component text, ConfigurationSection properties) {
    }

    private final DialogueHud hud;

    private final Conversation conversation;

    private final Player player;

    private final HudConfig config;

    private final ActionbarRenderer renderer;

    /** Creates the IO used for turns with too many answers, or {@code null} when there is none. */
    private final Supplier<@Nullable ConversationIO> longListIo;

    private Component npcName = Component.empty();

    private Component npcText = Component.empty();

    private final List<Option> options = new ArrayList<>();

    private Mode mode = Mode.WAITING;

    private Component prefix = Component.empty();

    /** Pages of NPC text, each a list of lines. */
    private List<List<Component>> pages = List.of();

    private int page;

    private int revealed;

    private int selected;

    private int cycleWidth;

    /** Frame height in lines for the current NPC turn. */
    private int cycleRows;

    private boolean dirty;

    private int ticksSinceSend;

    private int idleTicks;

    private boolean disposed;

    private boolean cancelledByPlayer;

    private boolean shownAnything;

    private long lastInput;

    @Nullable
    private ScheduledTask task;

    @Nullable
    private ConversationIO delegate;

    private boolean delegating;

    DialogueSession(final DialogueHud hud, final Conversation conversation, final Player player,
                    final HudConfig config, final ActionbarRenderer renderer,
                    final Supplier<@Nullable ConversationIO> longListIo) {
        this.hud = hud;
        this.conversation = conversation;
        this.player = player;
        this.config = config;
        this.renderer = renderer;
        this.longListIo = longListIo;
    }

    Player player() {
        return player;
    }

    HudConfig config() {
        return config;
    }

    // ---- ConversationIO, called by BetonQuest ----

    @Override
    public void begin() {
        hud.replaceSession(this);
        final ScheduledTask scheduled = player.getScheduler().runAtFixedRate(hud.plugin(), ignored -> tick(),
                this::dispose, 1, config.updateInterval());
        synchronized (this) {
            // The click that started the conversation must not also skip its first page.
            lastInput = System.currentTimeMillis();
            if (disposed || scheduled == null) {
                // The player left before the conversation got going.
                if (scheduled != null) {
                    scheduled.cancel();
                }
                return;
            }
            task = scheduled;
        }
    }

    @Override
    public synchronized void setNpcResponse(final Component npcName, final Component response) {
        this.npcName = npcName;
        this.npcText = response;
    }

    @Override
    public synchronized void addPlayerOption(final Component option, final ConfigurationSection properties) {
        options.add(new Option(option, properties));
    }

    @Override
    public void display() {
        ConversationIO longList = null;
        synchronized (this) {
            if (disposed) {
                return;
            }
            if (options.size() > config.maxHudOptions()) {
                longList = delegate();
            }
            if (longList == null) {
                showTurn();
                return;
            }
            delegating = true;
            clearActionbar();
            mode = Mode.WAITING;
        }
        handOver(longList);
    }

    @Override
    public void clear() {
        final ConversationIO handedOver;
        synchronized (this) {
            options.clear();
            npcText = Component.empty();
            mode = Mode.WAITING;
            dirty = true;
            handedOver = delegating ? delegate : null;
            delegating = false;
        }
        if (handedOver != null) {
            handedOver.clear();
        }
    }

    @Override
    public void end(final Runnable callback) {
        final ConversationIO handedOver;
        synchronized (this) {
            handedOver = delegate;
            // An NPC line without answers ends the conversation right after it is displayed;
            // keep it readable instead of wiping it before the typing effect is done.
            if (!cancelledByPlayer && options.isEmpty() && mode == Mode.READING
                    && config.epilogueTicks() > 0 && player.isOnline()) {
                mode = Mode.EPILOGUE;
                idleTicks = 0;
            } else {
                dispose();
            }
        }
        if (handedOver != null) {
            handedOver.end(() -> {
            });
        }
        callback.run();
    }

    // ---- Input, called by DialogueInputHandler on the player's thread ----

    /**
     * @return whether the input was used, so the vanilla action should be cancelled
     */
    boolean input(final HudConfig.Input input) {
        final int answer;
        synchronized (this) {
            if (disposed || mode == Mode.WAITING) {
                return false;
            }
            final long now = System.currentTimeMillis();
            final boolean bounced = now - lastInput < config.debounceMs();
            if (config.cancel().contains(input) && mode == Mode.EPILOGUE) {
                dispose();
                return true;
            } else if (config.cancel().contains(input)) {
                if (conversation.isMovementBlock()) {
                    return false;
                }
                cancelledByPlayer = true;
                answer = -1;
            } else if (mode == Mode.SELECTING && config.confirm().contains(input)) {
                if (bounced) {
                    return true;
                }
                answer = selected + 1;
                mode = Mode.WAITING;
            } else if (mode != Mode.SELECTING && config.next().contains(input)) {
                if (!bounced) {
                    next();
                    lastInput = now;
                }
                return true;
            } else {
                return false;
            }
            lastInput = now;
        }
        if (answer < 0) {
            conversation.endConversation();
        } else {
            conversation.passPlayerAnswer(answer);
        }
        return true;
    }

    /**
     * @return whether the scroll was used, so the held slot should stay
     */
    synchronized boolean scroll(final int delta) {
        if (disposed || mode != Mode.SELECTING || !config.scroll() || delta == 0) {
            return false;
        }
        selected = Math.floorMod(selected + delta, options.size());
        dirty = true;
        send();
        return true;
    }

    /**
     * Ends the HUD without touching the conversation, e.g. when the player leaves or the plugin stops.
     */
    synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (task != null) {
            task.cancel();
        }
        clearActionbar();
        hud.removeSession(this);
    }

    // ---- internals, all called with the monitor held ----

    private void showTurn() {
        prefix = renderer.prefix(npcName, conversation.getID().getFull());
        pages = renderer.paginate(prefix, npcText);
        page = 0;
        selected = 0;
        revealed = config.typing() ? 0 : Integer.MAX_VALUE;
        mode = pages.isEmpty() ? Mode.SELECTING : Mode.READING;
        if (pages.isEmpty() && options.isEmpty()) {
            mode = Mode.WAITING;
            return;
        }
        // One frame size for the whole turn, so the frame does not jump between pages and answers.
        cycleWidth = 0;
        cycleRows = renderer.frameRows(1);
        for (int i = 0; i < pages.size(); i++) {
            measure(renderer.readingLines(pages.get(i), true, i + 1, pages.size()));
        }
        for (int i = 0; i < options.size(); i++) {
            measure(optionLines(i));
        }
        dirty = true;
        send();
    }

    private void measure(final List<Component> lines) {
        cycleRows = Math.max(cycleRows, renderer.frameRows(lines.size()));
        for (final Component line : lines) {
            cycleWidth = Math.max(cycleWidth, renderer.width(line));
        }
    }

    private void next() {
        idleTicks = 0;
        if (!pageComplete()) {
            revealed = Integer.MAX_VALUE;
        } else if (page < pages.size() - 1) {
            page++;
            revealed = config.typing() ? 0 : Integer.MAX_VALUE;
        } else if (mode == Mode.EPILOGUE) {
            dispose();
            return;
        } else if (!options.isEmpty()) {
            mode = Mode.SELECTING;
        }
        dirty = true;
        send();
    }

    private synchronized void tick() {
        if (disposed) {
            return;
        }
        if ((mode == Mode.READING || mode == Mode.EPILOGUE) && !pageComplete()) {
            revealed += config.typingCharsPerUpdate();
            dirty = true;
        } else if (mode == Mode.EPILOGUE) {
            // Nobody may be listening any more, so the epilogue turns its own pages.
            idleTicks += config.updateInterval();
            if (idleTicks >= config.epilogueTicks()) {
                if (page == pages.size() - 1) {
                    dispose();
                    return;
                }
                idleTicks = 0;
                page++;
                revealed = config.typing() ? 0 : Integer.MAX_VALUE;
                dirty = true;
            }
        }
        ticksSinceSend += config.updateInterval();
        if (dirty || (shownAnything && mode != Mode.WAITING && ticksSinceSend >= config.keepalive())) {
            send();
        }
    }

    private boolean pageComplete() {
        return pages.isEmpty() || revealed >= ActionbarRenderer.length(pages.get(page));
    }

    private void send() {
        dirty = false;
        ticksSinceSend = 0;
        final List<Component> lines = switch (mode) {
            case WAITING -> null;
            case SELECTING -> optionLines(selected);
            case READING, EPILOGUE -> {
                final List<Component> text = ActionbarRenderer.reveal(pages.get(page), revealed);
                final boolean more = page < pages.size() - 1 || (mode == Mode.READING && !options.isEmpty());
                yield renderer.readingLines(text, more && pageComplete(), page + 1, pages.size());
            }
        };
        if (lines != null) {
            player.sendActionBar(renderer.finish(lines, cycleRows, cycleWidth));
            shownAnything = true;
        }
    }

    private List<Component> optionLines(final int selectedIndex) {
        return renderer.optionLines(prefix, options.stream().map(Option::text).toList(), selectedIndex);
    }

    private void clearActionbar() {
        if (shownAnything) {
            player.sendActionBar(Component.empty());
            shownAnything = false;
        }
    }

    @Nullable
    private ConversationIO delegate() {
        if (delegate == null) {
            delegate = longListIo.get();
            if (delegate != null) {
                delegate.begin();
            }
        }
        return delegate;
    }

    /**
     * Shows this turn in the long-list IO. Called without the monitor, as the IO calls back into
     * BetonQuest on its own.
     */
    private void handOver(final ConversationIO longList) {
        final Component name;
        final Component text;
        final List<Option> turn;
        synchronized (this) {
            name = npcName;
            text = npcText;
            turn = List.copyOf(options);
        }
        longList.setNpcResponse(name, text);
        for (final Option option : turn) {
            try {
                longList.addPlayerOption(option.text(), option.properties());
            } catch (final QuestException e) {
                hud.getLogger().warning("Long answer list IO rejected an answer: " + e.getMessage());
            }
        }
        longList.display();
    }
}
