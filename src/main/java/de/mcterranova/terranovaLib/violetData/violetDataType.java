package de.mcterranova.terranovaLib.violetData;

import de.mcterranova.terranovaLib.violetData.DataTypes.InstantDataType;
import de.mcterranova.terranovaLib.violetData.DataTypes.UUIDDataType;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.UUID;

public interface violetDataType {

    PersistentDataType<byte[], Instant> Instant = new InstantDataType();
    PersistentDataType<byte[], UUID> UUID = new UUIDDataType();

}
