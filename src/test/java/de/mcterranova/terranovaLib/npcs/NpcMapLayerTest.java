package de.mcterranova.terranovaLib.npcs;

import com.nekyia.heroicmap.api.Layer;
import com.nekyia.heroicmap.api.MapObject;
import com.nekyia.heroicmap.api.MapObject.Pin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NpcMapLayerTest {

    private static final String HEAD = "a".repeat(64);

    /** A layer that keeps what is put, and rejects some ids always and others only with a head. */
    private static final class FakeLayer {
        final Map<String, MapObject> objects = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        final Layer layer = (Layer) Proxy.newProxyInstance(Layer.class.getClassLoader(), new Class<?>[]{Layer.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "put" -> {
                            final Pin pin = (Pin) args[0];
                            calls.add("put " + pin.id());
                            if (pin.id().equals("npc-bad") || pin.id().equals("npc-badhead") && pin.head() != null) {
                                throw new IllegalArgumentException("rejected");
                            }
                            objects.put(pin.id(), pin);
                        }
                        case "remove" -> {
                            calls.add("remove " + args[0]);
                            objects.remove((String) args[0]);
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                    return null;
                });
    }

    private static Pin pin(final String id) {
        return Pin.at(id, 0.5, 0.5).withName(id).withHead(HEAD);
    }

    @Test
    void aRejectedPinDoesNotHoldUpTheOthers() {
        final FakeLayer fake = new FakeLayer();
        final NpcMapLayer map = new NpcMapLayer(fake.layer, Logger.getAnonymousLogger());

        map.apply(List.of(pin("npc-1"), pin("npc-bad"), pin("npc-badhead"), pin("npc-2")));

        assertEquals(HEAD, ((Pin) fake.objects.get("npc-1")).head());
        assertEquals(HEAD, ((Pin) fake.objects.get("npc-2")).head());
        assertNull(((Pin) fake.objects.get("npc-badhead")).head());
        assertNull(fake.objects.get("npc-bad"));
    }

    @Test
    void putsOnlyWhatChangedAndRemovesWhatIsGone() {
        final FakeLayer fake = new FakeLayer();
        final NpcMapLayer map = new NpcMapLayer(fake.layer, Logger.getAnonymousLogger());
        map.apply(List.of(pin("npc-1"), pin("npc-2"), pin("npc-badhead")));
        fake.calls.clear();

        map.apply(List.of(pin("npc-1").withY(70), pin("npc-badhead")));

        // npc-badhead is unchanged, so it is not tried again although it only went up without its head
        assertEquals(List.of("put npc-1", "remove npc-2"), fake.calls);
        assertEquals(70, ((Pin) fake.objects.get("npc-1")).y());
    }
}
