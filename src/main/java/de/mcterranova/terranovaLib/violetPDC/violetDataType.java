package de.mcterranova.terranovaLib.violetPDC;

import de.mcterranova.terranovaLib.violetPDC.DataTypes.InstantDataType;
import de.mcterranova.terranovaLib.violetPDC.DataTypes.UUIDDataType;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.UUID;

public interface violetDataType {

    PersistentDataType<byte[], Instant> Instant = new InstantDataType();
    PersistentDataType<byte[], UUID> UUID = new UUIDDataType();

}
